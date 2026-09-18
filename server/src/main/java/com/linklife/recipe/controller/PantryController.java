package com.linklife.recipe.controller;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.recipe.dto.PantryAddRequest;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.service.PantryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/pantry")
public class PantryController {

    private final PantryService pantryService;

    @GetMapping
    public Result<List<PantryItem>> list() {
        return Result.ok(pantryService.list(UserContext.requireUserId()));
    }

    @PostMapping
    public Result<PantryItem> add(@Valid @RequestBody PantryAddRequest request) {
        return Result.ok(pantryService.add(UserContext.requireUserId(),
                request.type(), request.name(), request.note()));
    }

    @DeleteMapping("/{itemId}")
    public Result<Void> delete(@PathVariable long itemId) {
        pantryService.delete(UserContext.requireUserId(), itemId);
        return Result.ok();
    }
}
