package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilterTest {

    private static OrderFilterConditions courier() {
        return OrderFilterConditions.of(List.of("ShipmentType=Courier"));
    }

    @Test
    @DisplayName("a filter shared with the store is visible to everyone")
    void sharedFilterIsVisibleToEveryone() {
        OrderFilter filter = OrderFilter.sharedWithStore("store-1", "Courier", courier());

        assertThat(filter.isSharedWithStore()).isTrue();
        assertThat(filter.isVisibleTo("user-1")).isTrue();
        assertThat(filter.isVisibleTo("user-2")).isTrue();
    }

    @Test
    @DisplayName("a private filter is visible only to its owner")
    void privateFilterIsVisibleOnlyToOwner() {
        OrderFilter filter = OrderFilter.ownedBy("store-1", "user-1", "Mine", courier());

        assertThat(filter.isSharedWithStore()).isFalse();
        assertThat(filter.getScope()).isEqualTo("user-1");
        assertThat(filter.isVisibleTo("user-1")).isTrue();
        assertThat(filter.isVisibleTo("user-2")).isFalse();
    }

    @Test
    @DisplayName("the same conditions in the same scope give the same key")
    void sameConditionsGiveTheSameKey() {
        OrderFilter first = OrderFilter.sharedWithStore("store-1", "Courier", courier());
        OrderFilter second = OrderFilter.sharedWithStore("store-1", "Kurier", courier());

        assertThat(first.getFilterKey()).isEqualTo(second.getFilterKey());
    }

    @Test
    @DisplayName("the same conditions in different scopes give different keys")
    void differentScopesGiveDifferentKeys() {
        OrderFilter shared = OrderFilter.sharedWithStore("store-1", "Courier", courier());
        OrderFilter mine = OrderFilter.ownedBy("store-1", "user-1", "Courier", courier());

        assertThat(shared.getFilterKey()).isNotEqualTo(mine.getFilterKey());
    }
}
