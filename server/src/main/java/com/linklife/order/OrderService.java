package com.linklife.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.linklife.notify.event.ItemClaimedEvent;
import com.linklife.notify.event.ItemDoneEvent;
import com.linklife.notify.event.SheetCompletedEvent;
import com.linklife.notify.event.SheetSharedEvent;
import com.linklife.user.UserService;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    @Lazy
    private OrderService self;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private static final String STATUS_SHARED = "SHARED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String ITEM_OPEN = "OPEN";
    private static final String ITEM_CLAIMED = "CLAIMED";
    private static final String ITEM_COOKING = "COOKING";
    private static final String ITEM_DONE = "DONE";

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
        eventPublisher.publishEvent(new SheetSharedEvent(
                circleId, sheet.getId(), title, userId, items.size()));
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
        OrderSheet sheet = requireSheet(sheetId);
        circleService.requireMembership(userId, sheet.getCircleId());
        return self.loadDetail(sheetId);
    }

    @org.springframework.cache.annotation.Cacheable(value = "sheetDetail", key = "#sheetId")
    public SheetDetailVO loadDetail(long sheetId) {
        OrderSheet sheet = orderSheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new BusinessException(ErrorCode.SHEET_NOT_FOUND);
        }
        return toDetailVO(sheet);
    }

    private void evictSheet(OrderSheet sheet) {
        org.springframework.cache.Cache c1 = cacheManager.getCache("sheetDetail");
        if (c1 != null) c1.evict(sheet.getId());
        org.springframework.cache.Cache c2 = cacheManager.getCache("shareView");
        // share_token 创建时必生成；null 防御兜底（不应发生）
        if (c2 != null && sheet.getShareToken() != null) c2.evict(sheet.getShareToken());
    }

    @Transactional
    public ItemVO addItem(long userId, long sheetId, String dishName, String note) {
        OrderSheet sheet = requireSheet(sheetId);
        circleService.requireMembership(userId, sheet.getCircleId());
        requireSheetNotCompleted(sheet);
        OrderItem item = new OrderItem();
        item.setSheetId(sheetId);
        item.setDishName(dishName);
        item.setNote(note);
        item.setItemStatus(ITEM_OPEN);
        orderItemMapper.insert(item);
        evictSheet(sheet);
        return toItemVO(item);
    }

    @Transactional
    public ItemVO claim(long userId, long itemId) {
        OrderItem item = requireItem(itemId);
        OrderSheet sheet = requireSheet(item.getSheetId());
        circleService.requireMembership(userId, sheet.getCircleId());
        requireSheetNotCompleted(sheet);
        if (!ITEM_OPEN.equals(item.getItemStatus())) {
            throw new BusinessException(ErrorCode.ITEM_STATUS_INVALID);
        }
        item.setClaimantId(userId);
        item.setItemStatus(ITEM_CLAIMED);
        orderItemMapper.updateById(item);
        if (STATUS_SHARED.equals(sheet.getStatus())) {
            orderSheetMapper.update(null, new LambdaUpdateWrapper<OrderSheet>()
                    .eq(OrderSheet::getId, sheet.getId())
                    .eq(OrderSheet::getStatus, STATUS_SHARED)
                    .set(OrderSheet::getStatus, STATUS_IN_PROGRESS));
        }
        eventPublisher.publishEvent(new ItemClaimedEvent(
                sheet.getId(), sheet.getTitle(), item.getId(), item.getDishName(),
                userId, sheet.getCreatorId()));
        evictSheet(sheet);
        return toItemVO(item);
    }

    @Transactional
    public ItemVO release(long userId, long itemId) {
        OrderItem item = requireItem(itemId);
        OrderSheet sheet = requireSheet(item.getSheetId());
        requireSheetNotCompleted(sheet);
        requireClaimant(userId, item);
        if (!ITEM_CLAIMED.equals(item.getItemStatus()) && !ITEM_COOKING.equals(item.getItemStatus())) {
            throw new BusinessException(ErrorCode.ITEM_STATUS_INVALID);
        }
        item.setClaimantId(null);
        item.setItemStatus(ITEM_OPEN);
        orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItem>()
                .eq(OrderItem::getId, item.getId())
                .set(OrderItem::getClaimantId, null)
                .set(OrderItem::getItemStatus, ITEM_OPEN));
        evictSheet(sheet);
        return toItemVO(item);
    }

    @Transactional
    public ItemVO updateItemStatus(long userId, long itemId, String target) {
        OrderItem item = requireItem(itemId);
        OrderSheet sheet = requireSheet(item.getSheetId());
        requireSheetNotCompleted(sheet);
        requireClaimant(userId, item);
        String current = item.getItemStatus();
        boolean valid = (ITEM_CLAIMED.equals(current)
                && (ITEM_COOKING.equals(target) || ITEM_DONE.equals(target)))
                || (ITEM_COOKING.equals(current) && ITEM_DONE.equals(target));
        if (!valid) {
            throw new BusinessException(ErrorCode.ITEM_STATUS_INVALID);
        }
        item.setItemStatus(target);
        orderItemMapper.updateById(item);
        if (ITEM_DONE.equals(target)) {
            eventPublisher.publishEvent(new ItemDoneEvent(
                    sheet.getId(), sheet.getTitle(), item.getId(), item.getDishName(),
                    userId, sheet.getCreatorId()));
        }
        evictSheet(sheet);
        return toItemVO(item);
    }

    @Transactional
    public SheetDetailVO completeSheet(long userId, long sheetId) {
        OrderSheet sheet = requireSheet(sheetId);
        if (!Objects.equals(sheet.getCreatorId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_ITEM_CLAIMANT);
        }
        requireSheetNotCompleted(sheet);
        sheet.setStatus(STATUS_COMPLETED);
        orderSheetMapper.updateById(sheet);
        eventPublisher.publishEvent(new SheetCompletedEvent(
                sheet.getCircleId(), sheet.getId(), sheet.getTitle(), userId));
        evictSheet(sheet);
        return toDetailVO(sheet);
    }

    private OrderSheet requireSheet(long sheetId) {
        OrderSheet sheet = orderSheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new BusinessException(ErrorCode.SHEET_NOT_FOUND);
        }
        return sheet;
    }

    private OrderItem requireItem(long itemId) {
        OrderItem item = orderItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    private void requireSheetNotCompleted(OrderSheet sheet) {
        if (STATUS_COMPLETED.equals(sheet.getStatus())) {
            throw new BusinessException(ErrorCode.SHEET_COMPLETED);
        }
    }

    private void requireClaimant(long userId, OrderItem item) {
        if (!Objects.equals(item.getClaimantId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_ITEM_CLAIMANT);
        }
    }

    private ItemVO toItemVO(OrderItem item) {
        String nickname = item.getClaimantId() == null ? null
                : userService.getNicknames(List.of(item.getClaimantId())).get(item.getClaimantId());
        return new ItemVO(item.getId(), item.getDishName(), item.getNote(),
                item.getClaimantId(), nickname, item.getItemStatus());
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

    SheetDetailVO toDetailVO(OrderSheet sheet) {
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
