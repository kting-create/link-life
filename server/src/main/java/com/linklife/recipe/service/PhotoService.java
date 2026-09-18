package com.linklife.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.circle.entity.Circle;
import com.linklife.circle.mapper.CircleMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.order.entity.Dish;
import com.linklife.order.mapper.DishMapper;
import com.linklife.recipe.dto.PhotoVO;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PhotoService {

    public static final int MAX_PHOTOS_PER_RECIPE = 50;

    private final RecipePhotoMapper photoMapper;
    private final RecipeVersionMapper recipeVersionMapper;
    private final RecipeService recipeService;
    private final DishMapper dishMapper;
    private final CircleMapper circleMapper;
    private final ImageStorageService imageStorage;

    @Transactional
    public PhotoVO upload(long userId, long recipeId, int stepNo, MultipartFile file) {
        Recipe recipe = recipeService.requireVisibleRecipe(userId, recipeId);
        requireStepNo(recipe, stepNo);
        Long count = photoMapper.selectCount(new LambdaQueryWrapper<RecipePhoto>()
                .eq(RecipePhoto::getRecipeId, recipeId));
        if (count != null && count >= MAX_PHOTOS_PER_RECIPE) {
            throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("read upload failed", e);
        }
        ImageStorageService.validate(bytes, file.getOriginalFilename());
        String filePath = imageStorage.save(recipeId, file.getOriginalFilename(), bytes);
        RecipePhoto photo = new RecipePhoto();
        photo.setRecipeId(recipeId);
        photo.setStepNo(stepNo);
        photo.setUploaderId(userId);
        photo.setFilePath(filePath);
        photo.setSizeBytes((long) bytes.length);
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        return toVO(photo);
    }

    public List<PhotoVO> list(long userId, long recipeId) {
        recipeService.requireVisibleRecipe(userId, recipeId);
        return photoMapper.selectList(new LambdaQueryWrapper<RecipePhoto>()
                        .eq(RecipePhoto::getRecipeId, recipeId)
                        .orderByAsc(RecipePhoto::getStepNo)
                        .orderByAsc(RecipePhoto::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public void delete(long userId, long photoId) {
        RecipePhoto photo = requireVisiblePhoto(userId, photoId);
        if (!Objects.equals(photo.getUploaderId(), userId)) {
            requireCircleOwner(userId, photo);
        }
        photoMapper.deleteById(photoId);
        imageStorage.delete(photo.getFilePath());
    }

    /** 供分析/应用复用：圈外用户按照片不存在处理。 */
    public RecipePhoto requireVisiblePhoto(long userId, long photoId) {
        RecipePhoto photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        }
        recipeService.requireVisibleRecipe(userId, photo.getRecipeId());
        return photo;
    }

    private void requireStepNo(Recipe recipe, int stepNo) {
        RecipeVersion version = recipeVersionMapper.selectOne(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipe.getId())
                        .eq(RecipeVersion::getVersion, recipe.getCurrentVersion()));
        if (version == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        boolean exists = RecipeService.parseContent(version.getContent()).steps().stream()
                .anyMatch(s -> s.no() != null && s.no() == stepNo);
        if (!exists) {
            throw new BusinessException(ErrorCode.PHOTO_STEP_INVALID);
        }
    }

    private void requireCircleOwner(long userId, RecipePhoto photo) {
        Recipe recipe = recipeService.requireVisibleRecipe(userId, photo.getRecipeId());
        Dish dish = dishMapper.selectById(recipe.getDishId());
        Circle circle = dish == null ? null : circleMapper.selectById(dish.getCircleId());
        if (circle == null || !Objects.equals(circle.getOwnerId(), userId)) {
            throw new BusinessException(ErrorCode.PHOTO_NO_PERMISSION);
        }
    }

    private PhotoVO toVO(RecipePhoto photo) {
        return new PhotoVO(photo.getId(), photo.getStepNo(), "/" + photo.getFilePath(),
                photo.getSizeBytes(), photo.getUploaderId(), photo.getAnalysis() != null,
                photo.getCreatedAt());
    }
}
