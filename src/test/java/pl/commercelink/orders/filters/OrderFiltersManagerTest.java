package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFiltersManagerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private OrderFiltersRepository orderFiltersRepository;

    @InjectMocks
    private OrderFiltersManager manager;

    @Test
    @DisplayName("a user sees the store wide filters and only their own private ones")
    void userSeesGlobalAndOwnFilters() {
        OrderFilter global = OrderFilter.global(STORE_ID, "Due today", List.of());
        OrderFilter mine = OrderFilter.ownedBy(STORE_ID, "user-1", "Mine", List.of());
        OrderFilter someoneElses = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", List.of());
        when(orderFiltersRepository.findAllByStoreId(STORE_ID)).thenReturn(List.of(global, mine, someoneElses));

        List<OrderFilter> visible = manager.visibleTo(STORE_ID, "user-1");

        assertThat(visible).containsExactly(global, mine);
    }

    @Test
    @DisplayName("a filter of another user cannot be opened by key")
    void filterOfAnotherUserIsNotFound() {
        OrderFilter someoneElses = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", List.of());
        when(orderFiltersRepository.findById(STORE_ID, someoneElses.getFilterKey())).thenReturn(someoneElses);

        assertThat(manager.find(STORE_ID, "user-1", someoneElses.getFilterKey())).isNull();
    }

    @Test
    @DisplayName("only an administrator can create a store wide filter")
    void onlyAdministratorCreatesGlobalFilter() {
        assertThatThrownBy(() -> manager.create(STORE_ID, "user-1", true, false, "Due today", List.of()))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        verify(orderFiltersRepository, never()).save(any());
    }

    @Test
    @DisplayName("a regular user creates a private filter")
    void regularUserCreatesPrivateFilter() {
        OrderFilter created = manager.create(STORE_ID, "user-1", false, false, "Mine", List.of());

        assertThat(created.isGlobal()).isFalse();
        assertThat(created.getOwner()).isEqualTo("user-1");
        verify(orderFiltersRepository).save(created);
    }

    @Test
    @DisplayName("a private filter cannot be removed by another user")
    void privateFilterIsRemovedOnlyByOwner() {
        OrderFilter someoneElses = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", List.of());
        when(orderFiltersRepository.findById(STORE_ID, someoneElses.getFilterKey())).thenReturn(someoneElses);

        assertThatThrownBy(() -> manager.delete(STORE_ID, "user-1", true, someoneElses.getFilterKey()))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        verify(orderFiltersRepository, never()).delete(any(OrderFilter.class));
    }

    @Test
    @DisplayName("only an administrator can remove a store wide filter")
    void onlyAdministratorRemovesGlobalFilter() {
        OrderFilter global = OrderFilter.global(STORE_ID, "Due today", List.of());
        when(orderFiltersRepository.findById(STORE_ID, global.getFilterKey())).thenReturn(global);

        assertThatThrownBy(() -> manager.delete(STORE_ID, "user-1", false, global.getFilterKey()))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        manager.delete(STORE_ID, "user-1", true, global.getFilterKey());

        verify(orderFiltersRepository).delete(global);
    }
}
