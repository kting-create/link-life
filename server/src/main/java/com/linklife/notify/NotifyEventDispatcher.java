package com.linklife.notify;

import com.linklife.circle.CircleService;
import com.linklife.notify.channel.BroadcastChannel;
import com.linklife.notify.channel.PersonalChannel;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.event.ItemClaimedEvent;
import com.linklife.notify.event.ItemDoneEvent;
import com.linklife.notify.event.SheetCompletedEvent;
import com.linklife.notify.event.SheetSharedEvent;
import com.linklife.user.UserService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyEventDispatcher {

    private final NotificationService notificationService;
    private final CircleService circleService;
    private final UserService userService;
    private final List<PersonalChannel> personalChannels;
    private final List<BroadcastChannel> broadcastChannels;

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSheetShared(SheetSharedEvent e) {
        String title = nickname(e.creatorId()) + " 发起点单「" + e.title() + "」";
        String content = "共 " + e.itemCount() + " 道菜，快来认领";
        dispatchToMembers(e.circleId(), e.creatorId(), "SHEET_SHARED", title, content, e.sheetId());
        broadcast(title + "，" + content);
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemClaimed(ItemClaimedEvent e) {
        notifyCreatorForItem(e.sheetId(), e.sheetTitle(), e.itemId(), e.dishName(),
                e.claimantId(), e.creatorId(), "ITEM_CLAIMED", "认领了");
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemDone(ItemDoneEvent e) {
        notifyCreatorForItem(e.sheetId(), e.sheetTitle(), e.itemId(), e.dishName(),
                e.claimantId(), e.creatorId(), "ITEM_DONE", "做完了");
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSheetCompleted(SheetCompletedEvent e) {
        String title = "点单「" + e.title() + "」已全部完成";
        String content = "🎉 收工开饭";
        dispatchToMembers(e.circleId(), e.creatorId(), "SHEET_COMPLETED", title, content, e.sheetId());
        broadcast(title + "，" + content);
    }

    private void dispatchToMembers(long circleId, long actorId, String type,
                                   String title, String content, long sheetId) {
        List<Long> recipients = circleService.listMemberIds(circleId).stream()
                .filter(id -> id != actorId).toList();
        List<Notification> notifications = new ArrayList<>();
        for (Long userId : recipients) {
            notifications.add(notificationService.persist(userId, type, title, content, sheetId, circleId));
        }
        dispatchPersonal(notifications);
    }

    private void notifyCreatorForItem(long sheetId, String sheetTitle, long itemId, String dishName,
                                      long claimantId, long creatorId, String type, String verb) {
        if (claimantId == creatorId) {
            return;
        }
        String title = nickname(claimantId) + " " + verb + "「" + dishName + "」";
        String content = "点单「" + sheetTitle + "」";
        Notification n = notificationService.persist(creatorId, type, title, content, sheetId, null);
        dispatchPersonal(List.of(n));
        broadcast(title + "，" + content);
    }

    private void dispatchPersonal(List<Notification> notifications) {
        for (PersonalChannel channel : personalChannels) {
            if (!channel.enabled()) {
                continue;
            }
            for (Notification n : notifications) {
                try {
                    channel.send(n);
                } catch (Exception ex) {
                    log.warn("notify channel {} send failed: {}",
                            channel.getClass().getSimpleName(), ex.getMessage());
                }
            }
        }
    }

    private void broadcast(String summary) {
        for (BroadcastChannel channel : broadcastChannels) {
            if (!channel.enabled()) {
                continue;
            }
            try {
                channel.broadcast(summary);
            } catch (Exception ex) {
                log.warn("notify channel {} broadcast failed: {}",
                        channel.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }

    private String nickname(long userId) {
        return userService.getNicknames(List.of(userId)).getOrDefault(userId, "用户" + userId);
    }
}
