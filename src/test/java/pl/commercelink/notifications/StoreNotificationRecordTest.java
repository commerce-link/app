package pl.commercelink.notifications;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StoreNotificationRecordTest {

    @Test
    void startsANewNotificationUnreadAndInTheUnreadIndexOfItsStore() {
        // given
        StoreNotification notification = new StoreNotification(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, "rma-7", "Allegro refunded the buyer for return REF/1");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 14, 15, 32);

        // when
        StoreNotificationRecord record = StoreNotificationRecord.unread("store-1", notification, createdAt);

        // then
        assertThat(record.getStoreId()).isEqualTo("store-1");
        assertThat(record.getNotificationId()).isEqualTo("MARKETPLACE_RETURN_REFUNDED:rma-7");
        assertThat(record.getType()).isEqualTo(StoreNotificationType.MARKETPLACE_RETURN_REFUNDED);
        assertThat(record.getSeverity()).isEqualTo(StoreNotificationSeverity.WARNING);
        assertThat(record.getObject()).isEqualTo("rma-7");
        assertThat(record.getMessage()).isEqualTo("Allegro refunded the buyer for return REF/1");
        assertThat(record.getCreatedAt()).isEqualTo(createdAt);
        assertThat(record.getReadAt()).isNull();
        assertThat(record.getUnreadStoreId()).isEqualTo("store-1");
        assertThat(record.isUnread()).isTrue();
    }

    @Test
    void isReadOnceItHasAReadDate() {
        // given
        StoreNotificationRecord record = new StoreNotificationRecord();

        // when
        record.setReadAt(LocalDateTime.of(2026, 9, 14, 16, 0));

        // then
        assertThat(record.isUnread()).isFalse();
    }
}
