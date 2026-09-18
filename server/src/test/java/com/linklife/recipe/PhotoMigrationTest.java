package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.linklife.IntegrationTestBase;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PhotoMigrationTest extends IntegrationTestBase {

    @Autowired
    private RecipePhotoMapper photoMapper;

    @Test
    void photoTableRoundTrip() {
        RecipePhoto photo = new RecipePhoto();
        photo.setRecipeId(1L);
        photo.setStepNo(1);
        photo.setUploaderId(1L);
        photo.setFilePath("images/recipes/1/a.jpg");
        photo.setSizeBytes(1024L);
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        assertNotNull(photo.getId());

        RecipePhoto loaded = photoMapper.selectById(photo.getId());
        assertEquals("images/recipes/1/a.jpg", loaded.getFilePath());
    }
}
