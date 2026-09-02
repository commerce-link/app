package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFilterHandlersTest {

    private static final String STORE_ID = "store-1";
    private static final List<OrderFilterCondition> COURIER =
            List.of(OrderFilterCondition.of(OrderFilterField.ShipmentType, "Courier").orElseThrow());
    private static final List<OrderFilterCondition> PICKUP_POINT =
            List.of(OrderFilterCondition.of(OrderFilterField.ShipmentType, "PickupPoint").orElseThrow());

    @Mock
    private OrderFiltersRepository repository;

    private static FilterActor user(String userId) {
        return new FilterActor(STORE_ID, userId, false);
    }

    private static FilterActor admin(String userId) {
        return new FilterActor(STORE_ID, userId, true);
    }

    private static OrderFilterConditions courier() {
        return OrderFilterConditions.of(COURIER);
    }

    @Nested
    class Listing {

        @Test
        @DisplayName("a user sees the filters shared with the store and only their own private ones")
        void userSeesSharedAndOwnFilters() {
            OrderFilter shared = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            OrderFilter mine = OrderFilter.ownedBy(STORE_ID, "user-1", "Mine", courier());
            OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
            when(repository.findAllByStoreId(STORE_ID)).thenReturn(List.of(shared, mine, theirs));

            VisibleOrderFilters visible = new ListOrderFiltersHandler(repository).handle(user("user-1"));

            assertThat(visible.all()).containsExactly(shared, mine);
        }

        @Test
        @DisplayName("the selected filter is picked from what was already read, without a second query")
        void selectedFilterComesFromTheSameRead() {
            OrderFilter shared = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
            when(repository.findAllByStoreId(STORE_ID)).thenReturn(List.of(shared, theirs));

            VisibleOrderFilters visible = new ListOrderFiltersHandler(repository).handle(user("user-1"));

            assertThat(visible.byKey(shared.getFilterKey())).contains(shared);
            assertThat(visible.byKey(theirs.getFilterKey())).isEmpty();
            assertThat(visible.byKey(null)).isEmpty();
            verify(repository, never()).findById(anyString(), anyString());
        }
    }

    @Nested
    class Creating {

        @Test
        @DisplayName("only an administrator shares a filter with the store")
        void onlyAdministratorSharesWithStore() {
            assertThatThrownBy(() -> new CreateOrderFilterHandler(repository)
                    .handle(user("user-1"), true, "Courier", COURIER))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("a regular user creates a private filter")
        void regularUserCreatesPrivateFilter() {
            when(repository.findById(anyString(), anyString())).thenReturn(Optional.empty());

            OrderFilter created = new CreateOrderFilterHandler(repository)
                    .handle(user("user-1"), false, "Mine", COURIER);

            assertThat(created.isSharedWithStore()).isFalse();
            assertThat(created.getScope()).isEqualTo("user-1");
            verify(repository).save(created);
        }

        @Test
        @DisplayName("the same conditions cannot be saved twice in one scope")
        void sameConditionsCannotBeSavedTwice() {
            OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            when(repository.findById(STORE_ID, existing.getFilterKey())).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> new CreateOrderFilterHandler(repository)
                    .handle(admin("user-1"), true, "Kurier", COURIER))
                    .isInstanceOf(OrderFilterInvalidException.class)
                    .hasMessageContaining("Courier");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("a filter needs a label")
        void filterNeedsALabel() {
            assertThatThrownBy(() -> new CreateOrderFilterHandler(repository)
                    .handle(user("user-1"), false, "  ", COURIER))
                    .isInstanceOf(OrderFilterInvalidException.class);
        }
    }

    @Nested
    class Updating {

        @Test
        @DisplayName("changing only the label keeps the same row")
        void changingOnlyTheLabelKeepsTheSameRow() {
            OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            when(repository.findById(STORE_ID, existing.getFilterKey())).thenReturn(Optional.of(existing));

            OrderFilter updated = new UpdateOrderFilterHandler(repository)
                    .handle(admin("user-1"), existing.getFilterKey(), "Kurier", COURIER);

            assertThat(updated.getFilterKey()).isEqualTo(existing.getFilterKey());
            assertThat(updated.getLabel()).isEqualTo("Kurier");
            verify(repository).save(existing);
            verify(repository, never()).delete(any(OrderFilter.class));
        }

        @Test
        @DisplayName("changing the conditions writes the new row before dropping the old one")
        void changingConditionsReplacesTheRow() {
            OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            when(repository.findById(STORE_ID, existing.getFilterKey())).thenReturn(Optional.of(existing));
            when(repository.findById(STORE_ID, OrderFilter.sharedWithStore(STORE_ID, "x",
                    OrderFilterConditions.of(PICKUP_POINT)).getFilterKey())).thenReturn(Optional.empty());

            OrderFilter updated = new UpdateOrderFilterHandler(repository)
                    .handle(admin("user-1"), existing.getFilterKey(), "Paczkomaty", PICKUP_POINT);

            assertThat(updated.getFilterKey()).isNotEqualTo(existing.getFilterKey());
            assertThat(updated.isSharedWithStore()).isTrue();

            InOrder writes = inOrder(repository);
            writes.verify(repository).save(updated);
            writes.verify(repository).delete(existing);
        }

        @Test
        @DisplayName("conditions cannot be changed into a filter that already exists")
        void conditionsCannotCollideWithAnExistingFilter() {
            OrderFilter existing = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            OrderFilter clash = OrderFilter.sharedWithStore(STORE_ID, "Paczkomaty", OrderFilterConditions.of(PICKUP_POINT));
            when(repository.findById(STORE_ID, existing.getFilterKey())).thenReturn(Optional.of(existing));
            when(repository.findById(STORE_ID, clash.getFilterKey())).thenReturn(Optional.of(clash));

            assertThatThrownBy(() -> new UpdateOrderFilterHandler(repository)
                    .handle(admin("user-1"), existing.getFilterKey(), "Cokolwiek", PICKUP_POINT))
                    .isInstanceOf(OrderFilterInvalidException.class)
                    .hasMessageContaining("Paczkomaty");

            verify(repository, never()).delete(any(OrderFilter.class));
        }

        @Test
        @DisplayName("a private filter can be changed only by its owner")
        void privateFilterIsChangedOnlyByOwner() {
            OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
            when(repository.findById(STORE_ID, theirs.getFilterKey())).thenReturn(Optional.of(theirs));

            assertThatThrownBy(() -> new UpdateOrderFilterHandler(repository)
                    .handle(admin("user-1"), theirs.getFilterKey(), "Mine now", COURIER))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Deleting {

        @Test
        @DisplayName("a private filter can be removed only by its owner")
        void privateFilterIsRemovedOnlyByOwner() {
            OrderFilter theirs = OrderFilter.ownedBy(STORE_ID, "user-2", "Theirs", courier());
            when(repository.findById(STORE_ID, theirs.getFilterKey())).thenReturn(Optional.of(theirs));

            assertThatThrownBy(() -> new DeleteOrderFilterHandler(repository)
                    .handle(admin("user-1"), theirs.getFilterKey()))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            verify(repository, never()).delete(any(OrderFilter.class));
        }

        @Test
        @DisplayName("any administrator removes a filter shared with the store")
        void anyAdministratorRemovesSharedFilter() {
            OrderFilter shared = OrderFilter.sharedWithStore(STORE_ID, "Courier", courier());
            when(repository.findById(STORE_ID, shared.getFilterKey())).thenReturn(Optional.of(shared));

            assertThatThrownBy(() -> new DeleteOrderFilterHandler(repository)
                    .handle(user("user-1"), shared.getFilterKey()))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            new DeleteOrderFilterHandler(repository).handle(admin("user-9"), shared.getFilterKey());

            verify(repository).delete(shared);
        }

        @Test
        @DisplayName("removing a filter that is already gone does nothing")
        void removingAMissingFilterDoesNothing() {
            when(repository.findById(STORE_ID, "STORE#gone")).thenReturn(Optional.empty());

            new DeleteOrderFilterHandler(repository).handle(admin("user-1"), "STORE#gone");

            verify(repository, never()).delete(any(OrderFilter.class));
        }
    }
}
