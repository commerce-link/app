package pl.commercelink.orders.filters.handlers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.exceptions.OrderFilterAccessDeniedException;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;
import pl.commercelink.orders.filters.OrderFilterWriteAccess;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.ListOrderFiltersView;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFilterHandlersTest {

    private static final String STORE_ID = "store-1";
    private static final List<OrderFilterCondition> COURIER =
            List.of(condition(OrderFilterField.ShipmentType, "Courier"));
    private static final List<OrderFilterCondition> PICKUP_POINT =
            List.of(condition(OrderFilterField.ShipmentType, "PickupPoint"));

    @Mock
    private OrderFiltersRepository repository;

    private static OrderFilterCondition condition(OrderFilterField field, String rawValue) {
        return new OrderFilterCondition(field, field.normalize(rawValue));
    }

    private static FilterActor user(String userId) {
        return new FilterActor(STORE_ID, userId, false);
    }

    private static FilterActor admin(String userId) {
        return new FilterActor(STORE_ID, userId, true);
    }

    private static OrderFilter filter(String label) {
        return OrderFilter.of(label, OrderFilterConditions.of(COURIER));
    }

    private static OwnedOrderFilters rowOf(String userId, OrderFilter... filters) {
        OwnedOrderFilters owned = OwnedOrderFilters.emptyFor(STORE_ID, userId);
        for (OrderFilter filter : filters) {
            owned.add(filter);
        }
        return owned;
    }

    private OrderFilterWriteAccess writeAccess() {
        return new OrderFilterWriteAccess(repository);
    }

    @Nested
    class Listing {

        @Test
        @DisplayName("the dashboard reads the store row and the caller's own row")
        void readsBothRows() {
            OrderFilter shared = filter("Courier");
            OrderFilter mine = filter("Mine");
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER))
                    .thenReturn(Optional.of(rowOf(OwnedOrderFilters.STORE_FILTER, shared)));
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(rowOf("user-1", mine)));

            ListOrderFiltersView visible = new ListOrderFiltersHandler(repository).handle(user("user-1"));

            assertThat(visible.sharedWithStore()).containsExactly(shared);
            assertThat(visible.own()).containsExactly(mine);
            assertThat(visible.byId(mine.getId())).contains(mine);
            assertThat(visible.byId("nothing")).isEmpty();
            assertThat(visible.byId(null)).isEmpty();
        }

        @Test
        @DisplayName("an owner without a row simply has no filters")
        void missingRowMeansNoFilters() {
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.empty());
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.empty());

            ListOrderFiltersView visible = new ListOrderFiltersHandler(repository).handle(user("user-1"));

            assertThat(visible.sharedWithStore()).isEmpty();
            assertThat(visible.own()).isEmpty();
        }
    }

    @Nested
    class Creating {

        @Test
        @DisplayName("a regular user appends to their own row")
        void regularUserAppendsToOwnRow() {
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.empty());

            OrderFilter created = new CreateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), false, "Mine", COURIER);

            assertThat(created.getId()).isNotBlank();
            verify(repository).save(any(OwnedOrderFilters.class));
        }

        @Test
        @DisplayName("only an administrator writes to the store row")
        void onlyAdministratorWritesToStoreRow() {
            assertThatThrownBy(() -> new CreateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), true, "Courier", COURIER))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("the same filter may be saved twice, duplicates are allowed")
        void duplicatesAreAllowed() {
            OwnedOrderFilters own = rowOf("user-1", filter("Courier"));
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(own));

            new CreateOrderFilterHandler(repository, writeAccess()).handle(user("user-1"), false, "Courier", COURIER);

            assertThat(own.getFilters()).hasSize(2);
        }

        @Test
        @DisplayName("an owner cannot keep more than twenty filters")
        void anOwnerCannotKeepMoreThanTwentyFilters() {
            OwnedOrderFilters own = rowOf("user-1");
            for (int i = 0; i < OwnedOrderFilters.LIMIT_PER_DOCUMENT; i++) {
                own.add(filter("Filter " + i));
            }
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(own));

            assertThatThrownBy(() -> new CreateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), false, "One too many", COURIER))
                    .isInstanceOf(OrderFilterInvalidException.class)
                    .hasMessageContaining("20");

            assertThat(own.getFilters()).hasSize(OwnedOrderFilters.LIMIT_PER_DOCUMENT);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("a filter needs a label")
        void filterNeedsALabel() {
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new CreateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), false, "  ", COURIER))
                    .isInstanceOf(OrderFilterInvalidException.class);
        }
    }

    @Nested
    class Updating {

        @Test
        @DisplayName("a filter is changed in place and the row is saved once")
        void filterIsChangedInPlace() {
            OrderFilter mine = filter("Courier");
            OwnedOrderFilters own = rowOf("user-1", mine);
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.empty());
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(own));

            OrderFilter updated = new UpdateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), mine.getId(), false, "Paczkomaty", PICKUP_POINT);

            assertThat(updated.getId()).isEqualTo(mine.getId());
            assertThat(updated.getLabel()).isEqualTo("Paczkomaty");
            assertThat(updated.getConditions()).containsExactly("ShipmentType=PICKUPPOINT");
            verify(repository).save(own);
            verify(repository, never()).delete(any(OwnedOrderFilters.class));
        }

        @Test
        @DisplayName("a filter shared with the store is changed only by an administrator")
        void sharedFilterIsChangedOnlyByAdministrator() {
            OrderFilter shared = filter("Courier");
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER))
                    .thenReturn(Optional.of(rowOf(OwnedOrderFilters.STORE_FILTER, shared)));

            assertThatThrownBy(() -> new UpdateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), shared.getId(), true, "Kurier", COURIER))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("ticking shared moves a private filter into the store row and keeps its id")
        void tickingSharedMovesTheFilter() {
            OrderFilter mine = filter("Courier");
            OwnedOrderFilters own = rowOf("user-1", mine);
            OwnedOrderFilters storeRow = rowOf(OwnedOrderFilters.STORE_FILTER);
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.of(storeRow));
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(own));

            OrderFilter moved = new UpdateOrderFilterHandler(repository, writeAccess())
                    .handle(admin("user-1"), mine.getId(), true, "Courier", COURIER);

            assertThat(moved.getId()).isEqualTo(mine.getId());
            assertThat(own.getFilters()).isEmpty();
            assertThat(storeRow.getFilters()).containsExactly(mine);
            verify(repository).save(storeRow);
            verify(repository).save(own);
        }

        @Test
        @DisplayName("a regular user cannot share a filter with the store by updating it")
        void regularUserCannotShareByUpdating() {
            OrderFilter mine = filter("Courier");
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.empty());
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(rowOf("user-1", mine)));

            assertThatThrownBy(() -> new UpdateOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), mine.getId(), true, "Courier", COURIER))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);
        }

        @Test
        @DisplayName("a filter of another user is not reachable")
        void filterOfAnotherUserIsNotReachable() {
            OrderFilter theirs = filter("Theirs");
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.empty());
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(rowOf("user-1")));

            assertThatThrownBy(() -> new UpdateOrderFilterHandler(repository, writeAccess())
                    .handle(admin("user-1"), theirs.getId(), false, "Mine now", COURIER))
                    .isInstanceOf(OrderFilterInvalidException.class);
        }
    }

    @Nested
    class Deleting {

        @Test
        @DisplayName("removing a filter leaves the rest of the row alone")
        void removingLeavesTheRestAlone() {
            OrderFilter first = filter("First");
            OrderFilter second = filter("Second");
            OwnedOrderFilters own = rowOf("user-1", first, second);
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.empty());
            when(repository.findByOwner(STORE_ID, "user-1")).thenReturn(Optional.of(own));

            new DeleteOrderFilterHandler(repository, writeAccess()).handle(user("user-1"), first.getId());

            assertThat(own.getFilters()).containsExactly(second);
            verify(repository).save(own);
        }

        @Test
        @DisplayName("only an administrator removes a filter shared with the store")
        void onlyAdministratorRemovesSharedFilter() {
            OrderFilter shared = filter("Courier");
            OwnedOrderFilters storeRow = rowOf(OwnedOrderFilters.STORE_FILTER, shared);
            when(repository.findByOwner(STORE_ID, OwnedOrderFilters.STORE_FILTER)).thenReturn(Optional.of(storeRow));

            assertThatThrownBy(() -> new DeleteOrderFilterHandler(repository, writeAccess())
                    .handle(user("user-1"), shared.getId()))
                    .isInstanceOf(OrderFilterAccessDeniedException.class);

            new DeleteOrderFilterHandler(repository, writeAccess()).handle(admin("user-9"), shared.getId());

            assertThat(storeRow.getFilters()).isEmpty();
            verify(repository).save(storeRow);
        }
    }
}
