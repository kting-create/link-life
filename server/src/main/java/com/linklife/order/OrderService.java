package com.linklife.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.circle.CircleService;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.order.dto.ItemInput;
import com.linklife.order.dto.ItemVO;
import com.linklife.order.dto.SheetDetailVO;
import com.linklife.order.entity.OrderItem;
import com.linklife.order.entity.OrderSheet;
import com.linklife.order.mapper.OrderItemMapper;
import com.linklife.order.mapper.OrderSheetMapper;
import com.linklife.user.UserService;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 12;
    private static final int MAX_TOKEN_RETRIES = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderSheetMapper orderSheetMapper;
    private final OrderItemMapper orderItemMapper;
    private final CircleService circleService;
    private final UserService userService;

    @Transactional
    public SheetDetailVO createSheet(long userId, long circleId, String title, List<ItemInput> items) {
        circleService.requireMembership(userId, circleId);

        OrderSheet sheet = new OrderSheet();
        sheet.setCircleId(circleId);
        sheet.setCreatorId(userId);
        sheet.setTitle(title);
        sheet.setStatus("SHARED");
        insertSheetWithToken(sheet);

        for (ItemInput item : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setSheetId(sheet.getId());
            orderItem.setDishName(item.dishName());
            orderItem.setNote(item.note());
            orderItem.setItemStatus("OPEN");
            orderItemMapper.insert(orderItem);
        }
        return toDetailVO(sheet);
    }

    public List<SheetDetailVO> listSheets(long userId, long circleId) {
        circleService.requireMembership(userId, circleId);
        List<OrderSheet> sheets = orderSheetMapper.selectList(
                new LambdaQueryWrapper<OrderSheet>()
                        .eq(OrderSheet::getCircleId, circleId)
                        .orderByDesc(OrderSheet::getId));
        if (sheets.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OrderItem>> itemsBySheet = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>()
                                .in(OrderItem::getSheetId, sheets.stream().map(OrderSheet::getId).toList()))
                .stream().collect(Collectors.groupingBy(OrderItem::getSheetId));
        Map<Long, String> nicknames = loadNicknames(itemsBySheet.values().stream()
                .flatMap(List::stream).map(OrderItem::getClaimantId).toList());
        return sheets.stream()
                .map(sheet -> toDetailVO(sheet, itemsBySheet.getOrDefault(sheet.getId(), List.of()), nicknames))
                .toList();
    }

    public SheetDetailVO getSheet(long userId, long sheetId) {
        OrderSheet sheet = orderSheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new BusinessException(ErrorCode.SHEET_NOT_FOUND);
        }
        circleService.requireMembership(userId, sheet.getCircleId());
        return toDetailVO(sheet);
    }

    private void insertSheetWithToken(OrderSheet sheet) {
        for (int i = 0; i < MAX_TOKEN_RETRIES; i++) {
            sheet.setShareToken(generateToken());
            try {
                orderSheetMapper.insert(sheet);
                return;
            } catch (DuplicateKeyException e) {
                sheet.setId(null);
            }
        }
        throw new IllegalStateException("生成 shareToken 失败");
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    private SheetDetailVO toDetailVO(OrderSheet sheet) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getSheetId, sheet.getId()));
        Map<Long, String> nicknames = loadNicknames(items.stream().map(OrderItem::getClaimantId).toList());
        return toDetailVO(sheet, items, nicknames);
    }

    private SheetDetailVO toDetailVO(OrderSheet sheet, List<OrderItem> items, Map<Long, String> nicknames) {
        List<ItemVO> itemVOs = items.stream()
                .map(item -> new ItemVO(
                        item.getId(),
                        item.getDishName(),
                        item.getNote(),
                        item.getClaimantId(),
                        item.getClaimantId() == null ? null : nicknames.get(item.getClaimantId()),
                        item.getItemStatus()))
                .toList();
        return new SheetDetailVO(sheet.getId(), sheet.getCircleId(), sheet.getCreatorId(),
                sheet.getTitle(), sheet.getStatus(), sheet.getShareToken(), itemVOs);
    }

    private Map<Long, String> loadNicknames(Collection<Long> claimantIds) {
        List<Long> ids = claimantIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        return userService.getNicknames(ids);
    }
}
