package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryListStateTest {

    private static Delivery warehouse() {
        Delivery delivery = new Delivery();
        delivery.setType(DeliveryType.WAREHOUSE);
        return delivery;
    }

    private static Delivery dropship(DeliveryTrackingState state) {
        Delivery delivery = new Delivery();
        delivery.setType(DeliveryType.DROPSHIP);
        if (state != null) {
            delivery.tracking().finish(state);
        }
        return delivery;
    }

    @Test
    void orderStatusWinsOverEverythingElse() {
        // given
        Delivery awaiting = warehouse();
        awaiting.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        Delivery unknown = warehouse();
        unknown.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        unknown.setOrderErrorMessage("timeout");
        Delivery dispatched = warehouse();
        dispatched.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        Delivery failed = dropship(DeliveryTrackingState.CANCELLED_BY_SUPPLIER);
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);

        // then
        assertThat(DeliveryListState.of(awaiting)).isEqualTo(DeliveryListState.AWAITING_APPROVAL);
        assertThat(DeliveryListState.of(unknown)).isEqualTo(DeliveryListState.OUTCOME_UNKNOWN);
        assertThat(DeliveryListState.of(dispatched)).isEqualTo(DeliveryListState.DISPATCHED);
        assertThat(DeliveryListState.of(failed)).isEqualTo(DeliveryListState.FAILED);
    }

    @Test
    void receivedDropshipMeansShippedToTheCustomer() {
        // given
        Delivery warehouse = warehouse();
        warehouse.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 0));
        Delivery dropship = dropship(DeliveryTrackingState.COMPLETED);
        dropship.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 0));

        // then
        assertThat(DeliveryListState.of(warehouse)).isEqualTo(DeliveryListState.RECEIVED);
        assertThat(DeliveryListState.of(dropship)).isEqualTo(DeliveryListState.SHIPPED_TO_CUSTOMER);
        assertThat(DeliveryListState.of(dropship).isReceived()).isTrue();
    }

    @Test
    void dropshipOnItsWayFollowsItsTracking() {
        assertThat(DeliveryListState.of(dropship(DeliveryTrackingState.CANCELLED_BY_SUPPLIER)))
                .isEqualTo(DeliveryListState.CANCELLED_BY_SUPPLIER);
        assertThat(DeliveryListState.of(dropship(DeliveryTrackingState.SHIPPED_WITHOUT_DATA)))
                .isEqualTo(DeliveryListState.SHIPPED_WITHOUT_DATA);
        assertThat(DeliveryListState.of(dropship(DeliveryTrackingState.UNSUPPORTED)))
                .isEqualTo(DeliveryListState.AWAITING_SHIPMENT);
        assertThat(DeliveryListState.of(dropship(null))).isEqualTo(DeliveryListState.AWAITING_SHIPMENT);
        assertThat(DeliveryListState.of(warehouse())).isEqualTo(DeliveryListState.IN_TRANSIT);
    }

    @Test
    void onlyStatesNeedingAnOperatorDecisionAreProblems() {
        assertThat(java.util.Arrays.stream(DeliveryListState.values()).filter(DeliveryListState::isProblem))
                .containsExactly(DeliveryListState.OUTCOME_UNKNOWN, DeliveryListState.FAILED,
                        DeliveryListState.CANCELLED_BY_SUPPLIER, DeliveryListState.SHIPPED_WITHOUT_DATA);
    }

    @Test
    void everyStateHasAToneAndAParameterThatParsesBack() {
        for (DeliveryListState state : DeliveryListState.values()) {
            assertThat(state.tone()).isIn("is-warn", "is-info", "is-bad", "is-ok");
            assertThat(DeliveryListState.parse(state.param())).contains(state);
            assertThat(state.messageKey()).isEqualTo("deliveries.list.state." + state.param());
        }
        assertThat(DeliveryListState.parse("nonsense")).isEmpty();
    }
}
