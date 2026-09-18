package com.linklife.recipe.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.ai.gateway.AiGatewayService;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.dto.PhotoAnalysis;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.dto.RecipeDetailVO;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import com.linklife.user.service.TasteProfileService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoAnalysisService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final PhotoService photoService;
    private final RecipeService recipeService;
    private final TasteProfileService tasteProfileService;
    private final ImageStorageService imageStorage;
    private final AiGatewayService aiGatewayService;
    private final RecipePhotoMapper photoMapper;

    public PhotoAnalysis analyze(long userId, long photoId) {
        RecipePhoto photo = photoService.requireVisiblePhoto(userId, photoId);
        RecipeDetailVO detail = recipeService.get(userId, photo.getRecipeId());
        String stepText = detail.content().steps().stream()
                .filter(s -> s.no() != null && s.no().equals(photo.getStepNo()))
                .map(RecipeContent.Step::text)
                .findFirst()
                .orElse("");
        String taste = tasteProfileService.getSummary(userId);
        String prompt = PhotoAnalysisPrompts.build(detail.dishName(), stepText, taste);
        byte[] bytes = imageStorage.read(photo.getFilePath());
        PhotoAnalysis result = aiGatewayService.callStructuredWithImage(
                userId, "photo_analysis", prompt, bytes,
                ImageStorageService.contentType(photo.getFilePath()), PhotoAnalysis.class);
        result.validate();
        photo.setAnalysis(toJson(result));
        photo.setAnalyzedAt(LocalDateTime.now());
        photoMapper.updateById(photo);
        return result;
    }

    public static PhotoAnalysis parseAnalysis(String json) {
        try {
            return MAPPER.readValue(json, PhotoAnalysis.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOTHING_TO_APPLY);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public int apply(long userId, long photoId) {
        RecipePhoto photo = photoService.requireVisiblePhoto(userId, photoId);
        if (photo.getAnalysis() == null) {
            throw new BusinessException(ErrorCode.NOTHING_TO_APPLY);
        }
        PhotoAnalysis analysis = parseAnalysis(photo.getAnalysis());
        return recipeService.applyPhotoPatch(userId, photo.getRecipeId(),
                analysis.validChanges(), analysis.advice());
    }

    public static String toJson(PhotoAnalysis analysis) {
        try {
            return MAPPER.writeValueAsString(analysis);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VISION_AI_FAILED);
        }
    }
}
