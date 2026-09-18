package com.linklife.recipe.controller;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.recipe.dto.PhotoAnalysis;
import com.linklife.recipe.dto.PhotoVO;
import com.linklife.recipe.service.PhotoAnalysisService;
import com.linklife.recipe.service.PhotoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoAnalysisService photoAnalysisService;

    @PostMapping("/api/recipes/{id}/steps/{stepNo}/photos")
    public Result<PhotoVO> upload(@PathVariable long id, @PathVariable int stepNo,
                                  @RequestParam("file") MultipartFile file) {
        return Result.ok(photoService.upload(UserContext.requireUserId(), id, stepNo, file));
    }

    @GetMapping("/api/recipes/{id}/photos")
    public Result<List<PhotoVO>> list(@PathVariable long id) {
        return Result.ok(photoService.list(UserContext.requireUserId(), id));
    }

    @DeleteMapping("/api/photos/{photoId}")
    public Result<Void> delete(@PathVariable long photoId) {
        photoService.delete(UserContext.requireUserId(), photoId);
        return Result.ok();
    }

    @PostMapping("/api/photos/{photoId}/analysis")
    public Result<PhotoAnalysis> analyze(@PathVariable long photoId) {
        return Result.ok(photoAnalysisService.analyze(UserContext.requireUserId(), photoId));
    }
}
