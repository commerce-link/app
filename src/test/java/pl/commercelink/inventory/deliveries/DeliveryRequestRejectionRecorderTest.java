package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryRequestRejectionRecorderTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "dd000010-0000-4000-8000-000000000010";

    @Mock private StoreNotificationService notifications;
    @Mock private OrderEventsRepository orderEvents;

    private DeliveryRequestRejectionRecorder recorder;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        SupplierLabels labels = new SupplierLabels(mock(StoresRepository.class));
        recorder = new DeliveryRequestRejectionRecorder(notifications, orderEvents, messages, labels);
    }

    private static Allocation allocation(String orderId, String itemId, int qty, double unitCost) {
        Allocation allocation = mock(Allocation.class);
        org.mockito.Mockito.lenient().when(allocation.getKey()).thenReturn(new AllocationKey(orderId, itemId, null));
        org.mockito.Mockito.lenient().when(allocation.getQty()).thenReturn(qty);
        org.mockito.Mockito.lenient().when(allocation.getTotalCost()).thenReturn(qty * unitCost);
        return allocation;
    }

    private static Delivery delivery(Allocation... allocations) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider("Acme");
        delivery.setAllocations(new java.util.LinkedList<>(List.of(allocations)));
        return delivery;
    }

    @Test
    void publishesAWarningWithTheReasonAndTheTotals() {
        // given
        Delivery delivery = delivery(allocation("o-1", "i-1", 1, 1299.0), allocation(null, "w-1", 4, 649.0));

        // when
        recorder.record(STORE_ID, delivery, "  dostawca nie ma towaru  ");

        // then
        ArgumentCaptor<StoreNotification> sent = ArgumentCaptor.forClass(StoreNotification.class);
        verify(notifications).publish(eq(STORE_ID), sent.capture());
        assertThat(sent.getValue().getSeverity()).isEqualTo(StoreNotificationSeverity.WARNING);
        assertThat(sent.getValue().getType()).isEqualTo(StoreNotificationType.DELIVERY_REQUEST_REJECTED);
        assertThat(sent.getValue().getObject()).isEqualTo(DELIVERY_ID);
        assertThat(sent.getValue().getMessage())
                .contains("Acme").contains("dd000010").contains("5 szt.").contains("3 895,00 PLN")
                .contains("Powód: dostawca nie ma towaru.").contains("Oczekujących dostaw");
    }

    @Test
    void leavesTheReasonOutWhenItIsBlank() {
        // given
        Delivery delivery = delivery(allocation("o-1", "i-1", 1, 10.0));

        // when
        recorder.record(STORE_ID, delivery, "   ");

        // then
        ArgumentCaptor<StoreNotification> sent = ArgumentCaptor.forClass(StoreNotification.class);
        verify(notifications).publish(eq(STORE_ID), sent.capture());
        assertThat(sent.getValue().getMessage()).doesNotContain("Powód");
    }

    @Test
    void writesOneHistoryEventPerCustomerOrderAndNoneForWarehouseLines() {
        // given
        Delivery delivery = delivery(allocation("o-1", "i-1", 1, 10.0), allocation("o-1", "i-2", 2, 10.0),
                allocation("o-2", "i-3", 1, 10.0), allocation(null, "w-1", 3, 10.0));

        // when
        recorder.record(STORE_ID, delivery, "brak towaru");

        // then
        ArgumentCaptor<OrderEvent> saved = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEvents, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(OrderEvent::getOrderId).containsExactly("o-1", "o-2");
        assertThat(saved.getAllValues()).allSatisfy(event -> {
            assertThat(event.getType()).isEqualTo(EventType.action);
            assertThat(event.getName()).isEqualTo(OrderEvent.DELIVERY_REQUEST_REJECTED);
            assertThat(event.getDetails()).isEqualTo("Acme · brak towaru");
        });
    }

    @Test
    void aWarehouseOnlyRequestStillNotifiesTheStore() {
        // given
        Delivery delivery = delivery(allocation(null, "w-1", 3, 10.0));

        // when
        recorder.record(STORE_ID, delivery, null);

        // then
        verify(notifications).publish(eq(STORE_ID), any(StoreNotification.class));
        verify(orderEvents, never()).save(any());
    }

    @Test
    void historyDetailsWithoutAReasonNameOnlyTheSupplier() {
        // given
        Delivery delivery = delivery(allocation("o-1", "i-1", 1, 10.0));

        // when
        recorder.record(STORE_ID, delivery, "");

        // then
        ArgumentCaptor<OrderEvent> saved = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEvents).save(saved.capture());
        assertThat(saved.getValue().getDetails()).isEqualTo("Acme");
    }

    @Test
    void aFailingNotificationDoesNotStopTheHistoryOrThrow() {
        // given
        Delivery delivery = delivery(allocation("o-1", "i-1", 1, 10.0));
        doThrow(new RuntimeException("dynamo down")).when(notifications).publish(any(), any());

        // when / then
        assertThatCode(() -> recorder.record(STORE_ID, delivery, "x")).doesNotThrowAnyException();
        verify(orderEvents).save(any(OrderEvent.class));
    }

    @Test
    void aFailingSupplierLabelLookupDoesNotThrowAndPublishesNothing() {
        // given
        StoresRepository stores = mock(StoresRepository.class);
        doThrow(new RuntimeException("dynamo down")).when(stores).findById(STORE_ID);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        DeliveryRequestRejectionRecorder failing =
                new DeliveryRequestRejectionRecorder(notifications, orderEvents, messages, new SupplierLabels(stores));
        Delivery delivery = delivery(allocation("o-1", "i-1", 1, 10.0));

        // when / then
        assertThatCode(() -> failing.record(STORE_ID, delivery, "x")).doesNotThrowAnyException();
        verify(notifications, never()).publish(any(), any());
        verify(orderEvents, never()).save(any());
    }

    @Test
    void theReasonIsTrimmedAndCappedAtFiveHundredCharacters() {
        // when / then
        assertThat(DeliveryRequestRejectionRecorder.normalizedReason(null)).isNull();
        assertThat(DeliveryRequestRejectionRecorder.normalizedReason("  \n ")).isNull();
        assertThat(DeliveryRequestRejectionRecorder.normalizedReason("  ok ")).isEqualTo("ok");
        assertThat(DeliveryRequestRejectionRecorder.normalizedReason("a".repeat(600))).hasSize(500);
    }
}
