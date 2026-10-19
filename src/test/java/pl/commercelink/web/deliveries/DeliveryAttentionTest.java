package pl.commercelink.web.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryType;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttentionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private static Delivery planned(LocalDate date) {
        Delivery delivery = new Delivery();
        delivery.setType(DeliveryType.WAREHOUSE);
        delivery.setProvider("Acme");
        delivery.setEstimatedDeliveryAt(date);
        return delivery;
    }

    private static boolean matches(DeliveryAttention attention, Delivery delivery) {
        return attention.matches(delivery, DeliveryListState.of(delivery), TODAY);
    }

    @Test
    void overdueCountsDeliveriesOnTheirWayOnly() {
        // given
        Delivery inTransit = planned(TODAY.minusDays(3));
        Delivery failed = planned(TODAY.minusDays(3));
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);

        // when / then
        assertThat(matches(DeliveryAttention.OVERDUE, inTransit)).isTrue();
        assertThat(matches(DeliveryAttention.OVERDUE, failed)).isFalse();
        assertThat(matches(DeliveryAttention.OVERDUE, planned(TODAY))).isFalse();
    }

    @Test
    void todayCountsDeliveriesPlannedForToday() {
        // when / then
        assertThat(matches(DeliveryAttention.TODAY, planned(TODAY))).isTrue();
        assertThat(matches(DeliveryAttention.TODAY, planned(TODAY.plusDays(1)))).isFalse();
    }

    @Test
    void invoiceCountsReceivedDeliveriesAwaitingAPurchaseInvoice() {
        // given
        Delivery received = planned(null);
        received.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 0));
        Delivery invoiced = planned(null);
        invoiced.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 0));
        invoiced.setInvoiced(true);

        // when / then
        assertThat(matches(DeliveryAttention.INVOICE, received)).isTrue();
        assertThat(matches(DeliveryAttention.INVOICE, invoiced)).isFalse();
    }

    @Test
    void problemFollowsTheStateFlag() {
        // given
        Delivery failed = planned(null);
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);

        // when / then
        assertThat(matches(DeliveryAttention.PROBLEM, failed)).isTrue();
        assertThat(matches(DeliveryAttention.PROBLEM, planned(null))).isFalse();
    }
}
