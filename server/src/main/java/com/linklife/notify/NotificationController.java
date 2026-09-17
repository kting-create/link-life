package com.linklife.notify;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.notify.dto.NotificationPageVO;
import com.linklife.notify.dto.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<NotificationPageVO> list(@RequestParam(required = false) Long afterId,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.list(UserContext.requireUserId(), afterId, size));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.ok(new UnreadCountVO(
                notificationService.unreadCount(UserContext.requireUserId())));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable long id) {
        notificationService.markRead(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(UserContext.requireUserId());
        return Result.ok();
    }
}
