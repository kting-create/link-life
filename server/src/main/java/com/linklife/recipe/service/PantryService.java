package com.linklife.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.mapper.PantryItemMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PantryService {

    private final PantryItemMapper pantryItemMapper;

    public List<PantryItem> list(long userId) {
        return pantryItemMapper.selectList(new LambdaQueryWrapper<PantryItem>()
                .eq(PantryItem::getUserId, userId)
                .orderByDesc(PantryItem::getId));
    }

    public PantryItem add(long userId, String type, String name, String note) {
        Long dup = pantryItemMapper.selectCount(new LambdaQueryWrapper<PantryItem>()
                .eq(PantryItem::getUserId, userId)
                .eq(PantryItem::getName, name));
        if (dup > 0) {
            throw new BusinessException(ErrorCode.PANTRY_ITEM_EXISTS);
        }
        PantryItem item = new PantryItem();
        item.setUserId(userId);
        item.setType(type);
        item.setName(name);
        item.setNote(note);
        item.setCreatedAt(LocalDateTime.now());
        pantryItemMapper.insert(item);
        return item;
    }

    public void delete(long userId, long itemId) {
        int rows = pantryItemMapper.delete(new LambdaQueryWrapper<PantryItem>()
                .eq(PantryItem::getId, itemId)
                .eq(PantryItem::getUserId, userId));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PANTRY_ITEM_NOT_FOUND);
        }
    }
}
