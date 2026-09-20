package com.linklife.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.circle.CircleService;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.order.entity.Dish;
import com.linklife.order.mapper.DishMapper;
import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.PhotoAnalysis;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.dto.RecipeDetailVO;
import com.linklife.recipe.dto.RecipeVersionVO;
import com.linklife.recipe.dto.VersionMetaVO;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeFeedback;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipeFeedbackMapper;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import com.linklife.user.service.TasteProfileService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    public static final int MAX_VERSIONS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RecipeMapper recipeMapper;
    private final RecipeVersionMapper recipeVersionMapper;
    private final RecipeFeedbackMapper recipeFeedbackMapper;
    private final DishMapper dishMapper;
    private final CircleService circleService;
    private final TasteProfileService tasteProfileService;

    @Autowired
    @Lazy
    private RecipeService self;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    // ---------- 权限与查询 ----------

    public Recipe requireVisibleRecipe(long userId, long recipeId) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        Dish dish = dishMapper.selectById(recipe.getDishId());
        if (dish == null || dish.getCircleId() == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        try {
            circleService.requireMembership(userId, dish.getCircleId());
        } catch (BusinessException e) {
            // 圈外按不存在处理
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return recipe;
    }

    public int versionCount(long recipeId) {
        Long count = recipeVersionMapper.selectCount(new LambdaQueryWrapper<RecipeVersion>()
                .eq(RecipeVersion::getRecipeId, recipeId));
        return count == null ? 0 : count.intValue();
    }

    public RecipeDetailVO get(long userId, long recipeId) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        return self.loadRecipeDetail(recipe.getId());
    }

    @org.springframework.cache.annotation.Cacheable(value = "recipeDetail", key = "#recipeId")
    public RecipeDetailVO loadRecipeDetail(long recipeId) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        Dish dish = dishMapper.selectById(recipe.getDishId());
        RecipeVersion current = requireVersionRow(recipeId, recipe.getCurrentVersion());
        return new RecipeDetailVO(recipe.getId(), dish.getId(), dish.getName(),
                recipe.getCustomName(), recipe.getCurrentVersion(),
                parseContent(current.getContent()), listVersionMetas(recipeId),
                recipe.getUpdatedAt());
    }

    private void evictRecipe(long recipeId) {
        org.springframework.cache.Cache c = cacheManager.getCache("recipeDetail");
        if (c != null) c.evict(recipeId);
    }

    public RecipeDetailVO findByDish(long userId, long circleId, String dishName) {
        circleService.requireMembership(userId, circleId);
        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, dishName));
        if (dish == null || dish.getRecipeId() == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return get(userId, dish.getRecipeId());
    }

    public List<VersionMetaVO> listVersions(long userId, long recipeId) {
        requireVisibleRecipe(userId, recipeId);
        return listVersionMetas(recipeId);
    }

    public RecipeVersionVO getVersion(long userId, long recipeId, int version) {
        requireVisibleRecipe(userId, recipeId);
        RecipeVersion row = requireVersionRow(recipeId, version);
        return new RecipeVersionVO(row.getVersion(), row.getSource(), row.getChangeNote(),
                parseContent(row.getContent()), row.getCreatedAt());
    }

    // ---------- 反馈 / 编辑 / 回滚 ----------

    @Transactional
    public void addFeedback(long userId, long recipeId, int score, String comment) {
        requireVisibleRecipe(userId, recipeId);
        RecipeFeedback feedback = new RecipeFeedback();
        feedback.setRecipeId(recipeId);
        feedback.setUserId(userId);
        feedback.setScore(score);
        feedback.setComment(comment);
        feedback.setCreatedAt(LocalDateTime.now());
        recipeFeedbackMapper.insert(feedback);
    }

    @Transactional
    public void edit(long userId, long recipeId, RecipeContent content,
                     String customName, String changeNote) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        if (content != null) {
            if (versionCount(recipeId) >= MAX_VERSIONS) {
                throw new BusinessException(ErrorCode.RECIPE_VERSION_LIMIT);
            }
            int next = versionCount(recipeId) + 1;
            insertVersion(recipeId, next, "MANUAL_EDIT", content, changeNote, userId);
            recipe.setCurrentVersion(next);
        }
        if (customName != null) {
            recipe.setCustomName(customName);
        }
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
        evictRecipe(recipeId);
    }

    @Transactional
    public void rollback(long userId, long recipeId, int version) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        requireVersionRow(recipeId, version);
        recipe.setCurrentVersion(version);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
        evictRecipe(recipeId);
    }

    // ---------- 供流式编排使用（Task 8） ----------

    @Transactional
    public long createRecipeWithV1(long userId, long circleId, String dishName,
                                   RecipeContent content) {
        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, dishName));
        if (dish == null) {
            dish = new Dish();
            dish.setCircleId(circleId);
            dish.setUserId(userId);
            dish.setName(dishName);
            try {
                dishMapper.insert(dish);
            } catch (DuplicateKeyException e) {
                // 并发 upsert：以唯一键 uk_circle_name 兜底后重查
                dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getCircleId, circleId).eq(Dish::getName, dishName));
            }
        }
        if (dish == null || dish.getRecipeId() != null) {
            throw new BusinessException(ErrorCode.RECIPE_ALREADY_EXISTS);
        }
        Recipe recipe = new Recipe();
        recipe.setDishId(dish.getId());
        recipe.setCurrentVersion(1);
        recipe.setCreatedBy(userId);
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.insert(recipe);
        insertVersion(recipe.getId(), 1, "AI_GENERATE", content, null, userId);

        // 条件更新防并发双 recipe：只有 recipe_id 还是 NULL 的一方成功
        int rows = dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, dish.getId())
                .isNull(Dish::getRecipeId)
                .set(Dish::getRecipeId, recipe.getId()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.RECIPE_ALREADY_EXISTS);
        }
        return recipe.getId();
    }

    public record IterateContext(String dishName, RecipeContent current,
                                 List<RecipeFeedback> feedbacks) {
    }

    public IterateContext prepareIterate(long userId, long recipeId) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        if (versionCount(recipeId) >= MAX_VERSIONS) {
            throw new BusinessException(ErrorCode.RECIPE_VERSION_LIMIT);
        }
        Dish dish = dishMapper.selectById(recipe.getDishId());
        RecipeVersion current = requireVersionRow(recipeId, recipe.getCurrentVersion());
        List<RecipeFeedback> feedbacks = recipeFeedbackMapper.selectList(
                new LambdaQueryWrapper<RecipeFeedback>()
                        .eq(RecipeFeedback::getRecipeId, recipeId)
                        .orderByDesc(RecipeFeedback::getId)
                        .last("LIMIT 5"));
        return new IterateContext(dish.getName(), parseContent(current.getContent()), feedbacks);
    }

    @Transactional
    public int saveIterated(long userId, long recipeId, IterationResult result,
                            String changeNote) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        int next = versionCount(recipeId) + 1;
        insertVersion(recipeId, next, "AI_ITERATE", result.recipe(), changeNote, userId);
        recipe.setCurrentVersion(next);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
        tasteProfileService.applySummary(userId, result.tasteSummary());
        evictRecipe(recipeId);
        return next;
    }

    @Transactional
    public int applyPhotoPatch(long userId, long recipeId, List<PhotoAnalysis.Change> changes,
                               String changeNote) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        if (versionCount(recipeId) >= MAX_VERSIONS) {
            throw new BusinessException(ErrorCode.RECIPE_VERSION_LIMIT);
        }
        RecipeVersion current = requireVersionRow(recipeId, recipe.getCurrentVersion());
        RecipeContent content = parseContent(current.getContent());
        List<RecipeContent.Step> newSteps = new ArrayList<>();
        boolean changed = false;
        for (RecipeContent.Step step : content.steps()) {
            RecipeContent.Step merged = step;
            for (PhotoAnalysis.Change c : changes) {
                if (c.stepNo() != null && c.stepNo().equals(step.no())) {
                    String text = c.text() == null || c.text().isBlank() ? step.text() : c.text();
                    Integer dur = c.durationSec() == null ? step.durationSec() : c.durationSec();
                    merged = new RecipeContent.Step(step.no(), text, dur);
                    changed = true;
                }
            }
            newSteps.add(merged);
        }
        if (!changed) {
            throw new BusinessException(ErrorCode.NOTHING_TO_APPLY);
        }
        RecipeContent result = new RecipeContent(content.servings(), content.totalMinutes(),
                content.ingredients(), content.seasonings(), newSteps, content.tips());
        int next = versionCount(recipeId) + 1;
        insertVersion(recipeId, next, "PHOTO_ANALYSIS", result,
                changeNote != null && changeNote.length() > 255
                        ? changeNote.substring(0, 255) : changeNote, userId);
        recipe.setCurrentVersion(next);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
        evictRecipe(recipeId);
        return next;
    }

    // ---------- 内部 ----------

    public static RecipeContent parseContent(String json) {
        try {
            return MAPPER.readValue(json, RecipeContent.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }

    public static String toJson(RecipeContent content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }

    private void insertVersion(long recipeId, int version, String source,
                               RecipeContent content, String changeNote, long userId) {
        RecipeVersion row = new RecipeVersion();
        row.setRecipeId(recipeId);
        row.setVersion(version);
        row.setSource(source);
        row.setContent(toJson(content));
        row.setChangeNote(changeNote);
        row.setCreatedBy(userId);
        row.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(row);
    }

    private List<VersionMetaVO> listVersionMetas(long recipeId) {
        return recipeVersionMapper.selectList(new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)
                        .orderByAsc(RecipeVersion::getVersion))
                .stream()
                .map(v -> new VersionMetaVO(v.getVersion(), v.getSource(), v.getChangeNote(),
                        v.getCreatedBy(), v.getCreatedAt()))
                .toList();
    }

    private RecipeVersion requireVersionRow(long recipeId, int version) {
        RecipeVersion row = recipeVersionMapper.selectOne(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)
                        .eq(RecipeVersion::getVersion, version));
        if (row == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return row;
    }
}
