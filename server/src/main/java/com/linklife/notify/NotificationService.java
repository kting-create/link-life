package com.linklife.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.notify.dto.NotificationPageVO;
import com.linklife.notify.dto.NotificationVO;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.mapper.NotificationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    @Transactional
    public Notification persist(long userId, String type, String title, String content,
                                Long sheetId, Long circleId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSheetId(sheetId);
        n.setCircleId(circleId);
        notificationMapper.insert(n);
        return n;
    }

    public NotificationPageVO list(long userId, Long afterId, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        List<Notification> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .lt(afterId != null, Notification::getId, afterId)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + limit));
        long unread = unreadCount(userId);
        List<NotificationVO> items = rows.stream().map(n -> new NotificationVO(
                n.getId(), n.getType(), n.getTitle(), n.getContent(),
                n.getSheetId(), n.getCircleId(), n.getIsRead() != null && n.getIsRead() == 1,
                n.getCreatedAt() == null ? null : n.getCreatedAt().toString())).toList();
        return new NotificationPageVO(items, unread);
    }

    public long unreadCount(long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(long userId, long id) {
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        if (updated == 0) {
            Long exists = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId));
            if (exists == null || exists == 0) {
                throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
            }
        }
    }

    @Transactional
    public void markAllRead(long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
