package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFiltersManagerTest {

    private static final String STORE_ID = "store-1";
    private static final List<String> COURIER = List.of("ShipmentType=Courier");

    @Mock
    private OrderFiltersRepository orderFiltersRepository;

    @InjectMocks
    private OrderFiltersManager manager;

    private static OrderFilterConditions courier() {
        return OrderFilterConditions.of(COURIER);
    }

    @Test
    @DisplayName("a user sees the filters shared with the store and only their own private ones")
    void userSeesSharedAndOwnFilters() {
        OrderFilter shared = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
        OrderFilter mine = OrderFilter.ownedBy(STORE_ID, "user-1", "Mine", courier());
        OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
        when(orderFiltersRepository.findAllByStoreId(STORE_ID)).thenReturn(List.of(shared, mine, theirs));

        assertThat(manager.visibleTo(STORE_ID, "user-1")).containsExactly(shared, mine);
    }

    @Test
    @DisplayName("a filter of another user cannot be opened by key")
    void filterOfAnotherUserIsNotFound() {
        OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
        when(orderFiltersRepository.findById(STORE_ID, theirs.getFilterKey())).thenReturn(theirs);

        assertThat(manager.find(STORE_ID, "user-1", theirs.getFilterKey())).isNull();
    }

    @Test
    @DisplayName("only an administrator shares a filter with the store")
    void onlyAdministratorSharesWithStore() {
        assertThatThrownBy(() -> manager.create(STORE_ID, "user-1", true, false, "Courier", COURIER))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        verify(orderFiltersRepository, never()).save(any());
    }

    @Test
    @DisplayName("a regular user creates a private filter")
    void regularUserCreatesPrivateFilter() {
        OrderFilter created = manager.create(STORE_ID, "user-1", false, false, "Mine", COURIER);

        assertThat(created.isSharedWithStore()).isFalse();
        assertThat(created.getScope()).isEqualTo("user-1");
        verify(orderFiltersRepository).save(created);
    }

    @Test
    @DisplayName("the same conditions cannot be saved twice in one scope")
    void sameConditionsCannotBeSavedTwice() {
        OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
        when(orderFiltersRepository.findById(STORE_ID, existing.getFilterKey())).thenReturn(existing);

        assertThatThrownBy(() -> manager.create(STORE_ID, "user-1", true, true, "Kurier", COURIER))
                .isInstanceOf(OrderFilterInvalidException.class)
                .hasMessageContaining("Courier");

        verify(orderFiltersRepository, never()).save(any());
    }

    @Test
    @DisplayName("a filter needs a label")
    void filterNeedsALabel() {
        assertThatThrownBy(() -> manager.create(STORE_ID, "user-1", false, false, "  ", COURIER))
                .isInstanceOf(OrderFilterInvalidException.class);
    }

    @Test
    @DisplayName("changing only the label keeps the same row")
    void changingOnlyTheLabelKeepsTheSameRow() {
        OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
        when(orderFiltersRepository.findById(STORE_ID, existing.getFilterKey())).thenReturn(existing);

        OrderFilter updated = manager.update(STORE_ID, "user-1", true, existing.getFilterKey(), "Kurier", COURIER);

        assertThat(updated.getFilterKey()).isEqualTo(existing.getFilterKey());
        assertThat(updated.getLabel()).isEqualTo("Kurier");
        verify(orderFiltersRepository).save(existing);
        verify(orderFiltersRepository, never()).delete(any(OrderFilter.class));
    }

    @Test
    @DisplayName("changing the conditions writes the new row before dropping the old one")
    void changingConditionsReplacesTheRow() {
        OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
        when(orderFiltersRepository.findById(STORE_ID, existing.getFilterKey())).thenReturn(existing);

        OrderFilter updated = manager.update(STORE_ID, "user-1", true, existing.getFilterKey(),
                "Paczkomaty", List.of("ShipmentType=PickupPoint"));

        assertThat(updated.getFilterKey()).isNotEqualTo(existing.getFilterKey());
        assertThat(updated.isSharedWithStore()).isTrue();

        InOrder writes = inOrder(orderFiltersRepository);
        writes.verify(orderFiltersRepository).save(updated);
        writes.verify(orderFiltersRepository).delete(existing);
    }

    @Test
    @DisplayName("conditions cannot be changed into a filter that already exists")
    void conditionsCannotCollideWithAnExistingFilter() {
        OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
        OrderFilter clash = OrderFilter.sharedWithStore(STORE_ID, "Paczkomaty",
                OrderFilterConditions.of(List.of("ShipmentType=PickupPoint")));
        when(orderFiltersRepository.findById(STORE_ID, existing.getFilterKey())).thenReturn(existing);
        when(orderFiltersRepository.findById(STORE_ID, clash.getFilterKey())).thenReturn(clash);

        assertThatThrownBy(() -> manager.update(STORE_ID, "user-1", true, existing.getFilterKey(),
                "Cokolwiek", List.of("ShipmentType=PickupPoint")))
                .isInstanceOf(OrderFilterInvalidException.class)
                .hasMessageContaining("Paczkomaty");

        verify(orderFiltersRepository, never()).delete(any(OrderFilter.class));
    }

    @Test
    @DisplayName("a private filter can be changed only by its owner")
    void privateFilterIsChangedOnlyByOwner() {
        OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
        when(orderFiltersRepository.findById(STORE_ID, theirs.getFilterKey())).thenReturn(theirs);

        assertThatThrownBy(() -> manager.update(STORE_ID, "user-1", true, theirs.getFilterKey(),
                "Mine now", COURIER))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        verify(orderFiltersRepository, never()).save(any());
    }

    @Test
    @DisplayName("a private filter can be removed only by its owner")
    void privateFilterIsRemovedOnlyByOwner() {
        OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
        when(orderFiltersRepository.findById(STORE_ID, theirs.getFilterKey())).thenReturn(theirs);

        assertThatThrownBy(() -> manager.delete(STORE_ID, "user-1", true, theirs.getFilterKey()))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        verify(orderFiltersRepository, never()).delete(any(OrderFilter.class));
    }

    @Test
    @DisplayName("any administrator removes a filter shared with the store")
    void anyAdministratorRemovesSharedFilter() {
        OrderFilter shared = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
        when(orderFiltersRepository.findById(STORE_ID, shared.getFilterKey())).thenReturn(shared);

        assertThatThrownBy(() -> manager.delete(STORE_ID, "user-1", false, shared.getFilterKey()))
                .isInstanceOf(OrderFilterAccessDeniedException.class);

        manager.delete(STORE_ID, "user-9", true, shared.getFilterKey());

        verify(orderFiltersRepository).delete(shared);
    }
}
