package com.linklife.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.order.dto.SheetDetailVO;
import com.linklife.order.entity.OrderSheet;
import com.linklife.order.mapper.OrderSheetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final OrderSheetMapper orderSheetMapper;
    private final OrderService orderService;

    @org.springframework.cache.annotation.Cacheable(value = "shareView", key = "#token")
    public SheetDetailVO getByToken(String token) {
        OrderSheet sheet = orderSheetMapper.selectOne(
                new LambdaQueryWrapper<OrderSheet>().eq(OrderSheet::getShareToken, token));
        if (sheet == null) {
            throw new BusinessException(ErrorCode.SHEET_NOT_FOUND);
        }
        return orderService.toDetailVO(sheet);
    }
}
