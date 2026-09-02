package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilterTest {

    @Test
    @DisplayName("a global filter is owned by nobody and visible to everyone")
    void globalFilterIsVisibleToEveryone() {
        OrderFilter filter = OrderFilter.global("store-1", "Due today", List.of());

        assertThat(filter.isGlobal()).isTrue();
        assertThat(filter.isVisibleTo("user-1")).isTrue();
        assertThat(filter.isVisibleTo("user-2")).isTrue();
        assertThat(filter.getFilterKey()).startsWith(OrderFilter.GLOBAL_OWNER + "#");
    }

    @Test
    @DisplayName("a private filter is visible only to its owner")
    void privateFilterIsVisibleOnlyToOwner() {
        OrderFilter filter = OrderFilter.ownedBy("store-1", "user-1", "Mine", List.of());

        assertThat(filter.isGlobal()).isFalse();
        assertThat(filter.getOwner()).isEqualTo("user-1");
        assertThat(filter.isVisibleTo("user-1")).isTrue();
        assertThat(filter.isVisibleTo("user-2")).isFalse();
    }

    @Test
    @DisplayName("two filters of one user get distinct keys")
    void filtersOfOneUserGetDistinctKeys() {
        OrderFilter first = OrderFilter.ownedBy("store-1", "user-1", "First", List.of());
        OrderFilter second = OrderFilter.ownedBy("store-1", "user-1", "Second", List.of());

        assertThat(first.getFilterKey()).isNotEqualTo(second.getFilterKey());
    }
}
