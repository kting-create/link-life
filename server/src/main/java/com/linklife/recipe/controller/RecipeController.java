package com.linklife.recipe.controller;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.recipe.dto.EditRecipeRequest;
import com.linklife.recipe.dto.FeedbackRequest;
import com.linklife.recipe.dto.RecipeDetailVO;
import com.linklife.recipe.dto.RecipeVersionVO;
import com.linklife.recipe.dto.RollbackRequest;
import com.linklife.recipe.dto.VersionMetaVO;
import com.linklife.recipe.service.RecipeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping("/{id}")
    public Result<RecipeDetailVO> get(@PathVariable long id) {
        return Result.ok(recipeService.get(UserContext.requireUserId(), id));
    }

    @GetMapping("/by-dish")
    public Result<RecipeDetailVO> byDish(@RequestParam long circleId,
                                         @RequestParam String dishName) {
        return Result.ok(recipeService.findByDish(UserContext.requireUserId(),
                circleId, dishName));
    }

    @GetMapping("/{id}/versions")
    public Result<List<VersionMetaVO>> versions(@PathVariable long id) {
        return Result.ok(recipeService.listVersions(UserContext.requireUserId(), id));
    }

    @GetMapping("/{id}/versions/{version}")
    public Result<RecipeVersionVO> version(@PathVariable long id, @PathVariable int version) {
        return Result.ok(recipeService.getVersion(UserContext.requireUserId(), id, version));
    }

    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable long id,
                                 @Valid @RequestBody FeedbackRequest request) {
        recipeService.addFeedback(UserContext.requireUserId(), id,
                request.score(), request.comment());
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> edit(@PathVariable long id,
                             @Valid @RequestBody EditRecipeRequest request) {
        recipeService.edit(UserContext.requireUserId(), id,
                request.content(), request.customName(), request.changeNote());
        return Result.ok();
    }

    @PostMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable long id,
                                 @Valid @RequestBody RollbackRequest request) {
        recipeService.rollback(UserContext.requireUserId(), id, request.version());
        return Result.ok();
    }
}
