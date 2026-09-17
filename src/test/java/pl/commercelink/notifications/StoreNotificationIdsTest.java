package pl.commercelink.notifications;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.StoreNotificationType;

import static org.assertj.core.api.Assertions.assertThat;

class StoreNotificationIdsTest {

    @Test
    void identifiesANotificationByItsTypeAndObject() {
        // when
        String id = StoreNotificationIds.of(StoreNotificationType.UNAUTHENTICATED, "allegro_marketplace", "Any message");

        // then
        assertThat(id).isEqualTo("UNAUTHENTICATED:allegro_marketplace");
    }

    @Test
    void identifiesANotificationWithoutAnObjectByAShortHashOfItsMessage() {
        // when
        String first = StoreNotificationIds.of(StoreNotificationType.WELCOME, " ", "Welcome to CommerceLink");
        String again = StoreNotificationIds.of(StoreNotificationType.WELCOME, null, "Welcome to CommerceLink");
        String other = StoreNotificationIds.of(StoreNotificationType.WELCOME, null, "Another message");

        // then
        assertThat(first).matches("WELCOME:[0-9a-f]{16}");
        assertThat(again).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void identifiesALegacyNotificationWithoutATypeUnderAnUnknownPrefix() {
        // when / then
        assertThat(StoreNotificationIds.of(null, "ret-1", "Old message")).isEqualTo("UNKNOWN:ret-1");
    }
}
