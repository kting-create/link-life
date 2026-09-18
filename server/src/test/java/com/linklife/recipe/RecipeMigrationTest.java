package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.IntegrationTestBase;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeFeedback;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.PantryItemMapper;
import com.linklife.recipe.mapper.RecipeFeedbackMapper;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RecipeMigrationTest extends IntegrationTestBase {

    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private RecipeVersionMapper recipeVersionMapper;
    @Autowired
    private RecipeFeedbackMapper recipeFeedbackMapper;
    @Autowired
    private PantryItemMapper pantryItemMapper;

    @Test
    void tablesExistAndBasicInsertWorks() {
        Recipe recipe = new Recipe();
        recipe.setDishId(1L);
        recipe.setCurrentVersion(1);
        recipe.setCreatedBy(1L);
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.insert(recipe);
        assertNotNull(recipe.getId());

        RecipeVersion version = new RecipeVersion();
        version.setRecipeId(recipe.getId());
        version.setVersion(1);
        version.setSource("AI_GENERATE");
        version.setContent("{\"servings\":2}");
        version.setCreatedBy(1L);
        version.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(version);

        RecipeFeedback feedback = new RecipeFeedback();
        feedback.setRecipeId(recipe.getId());
        feedback.setUserId(1L);
        feedback.setScore(5);
        feedback.setCreatedAt(LocalDateTime.now());
        recipeFeedbackMapper.insert(feedback);

        PantryItem item = new PantryItem();
        item.setUserId(1L);
        item.setType("SEASONING");
        item.setName("生抽");
        item.setCreatedAt(LocalDateTime.now());
        pantryItemMapper.insert(item);

        assertEquals(1, recipeVersionMapper.selectCount(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipe.getId())));
    }
}
