package pl.commercelink.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationType;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class StoreNotificationService {

    static final Duration RETENTION = Duration.ofDays(90);

    private final StoreNotificationsRepository repository;
    private final Clock clock;

    @Autowired
    public StoreNotificationService(StoreNotificationsRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    StoreNotificationService(StoreNotificationsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void publish(String storeId, StoreNotification notification) {
        // an existing record wins even when it was read, so a repeated event never comes back as unread
        repository.putIfAbsent(StoreNotificationRecord.unread(storeId, notification, now()));
    }

    public void resolve(String storeId, StoreNotificationType type, String object) {
        repository.delete(storeId, StoreNotificationIds.of(type, object, null));
    }

    public long unreadCount(String storeId) {
        return repository.countUnread(storeId);
    }

    public List<StoreNotificationRecord> latest(String storeId, int limit) {
        return activeNewestFirst(storeId).stream().limit(limit).toList();
    }

    public NotificationPage list(String storeId, NotificationFilter filter, int page, int pageSize) {
        List<StoreNotificationRecord> active = activeNewestFirst(storeId);
        long unreadCount = active.stream().filter(StoreNotificationRecord::isUnread).count();
        List<StoreNotificationRecord> matching = active.stream().filter(filter::matches).toList();
        int totalPages = Math.max(1, (matching.size() + pageSize - 1) / pageSize);
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int from = (currentPage - 1) * pageSize;
        List<StoreNotificationRecord> items = matching.subList(from, Math.min(from + pageSize, matching.size()));
        return new NotificationPage(items, currentPage, totalPages, matching.size(), unreadCount);
    }

    public Optional<StoreNotificationRecord> find(String storeId, String notificationId) {
        return repository.find(storeId, notificationId);
    }

    public int markRead(String storeId, Collection<String> notificationIds) {
        LocalDateTime readAt = now();
        return (int) new LinkedHashSet<>(notificationIds).stream()
                .filter(notificationId -> repository.markRead(storeId, notificationId, readAt))
                .count();
    }

    public int markUnread(String storeId, Collection<String> notificationIds) {
        return (int) new LinkedHashSet<>(notificationIds).stream()
                .filter(notificationId -> repository.markUnread(storeId, notificationId))
                .count();
    }

    public int markAllRead(String storeId) {
        return markRead(storeId, repository.findUnreadIds(storeId));
    }

    // retention is enforced on read: the application role cannot enable DynamoDB TTL on the table
    private List<StoreNotificationRecord> activeNewestFirst(String storeId) {
        LocalDateTime expiredBefore = now().minus(RETENTION);
        List<StoreNotificationRecord> active = new ArrayList<>();
        for (StoreNotificationRecord record : repository.findAll(storeId)) {
            if (record.getReadAt() != null && record.getReadAt().isBefore(expiredBefore)) {
                repository.delete(storeId, record.getNotificationId());
            } else {
                active.add(record);
            }
        }
        active.sort(Comparator.comparing(StoreNotificationRecord::getCreatedAt).reversed());
        return active;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
