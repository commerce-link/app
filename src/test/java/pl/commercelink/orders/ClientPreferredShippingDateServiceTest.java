package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.ClientPreferredShippingDateException.Reason;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClientPreferredShippingDateServiceTest {

    // Monday
    private static final LocalDate ESTIMATED = LocalDate.of(2026, 9, 21);

    @Mock
    private OrdersRepository ordersRepository;

    @InjectMocks
    private ClientPreferredShippingDateService service;

    @Test
    @DisplayName("isEditable requires both the store flag and the order rule")
    void editableRequiresFlagAndOrderRule() {
        // when / then
        assertThat(service.isEditable(editableOrder(), store(true, true))).isTrue();
        assertThat(service.isEditable(editableOrder(), store(true, false))).isFalse();
        assertThat(service.isEditable(editableOrder(), store(false, true))).isFalse();
        Order shipping = editableOrder();
        shipping.setStatus(OrderStatus.Shipping);
        assertThat(service.isEditable(shipping, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("change saves a weekday inside the window")
    void changeSavesDateAndEvent() {
        // given
        Order order = editableOrder();
        LocalDate requested = ESTIMATED.plusDays(2);

        // when
        service.change(order, requested, store(true, true));

        // then
        assertThat(order.getPreferredShippingAt()).isEqualTo(requested);
        verify(ordersRepository).save(order);
    }

    @Test
    @DisplayName("change with no date clears the preference")
    void changeWithNullClears() {
        // given
        Order order = editableOrder();
        order.setPreferredShippingAt(ESTIMATED.plusDays(2));

        // when
        service.change(order, null, store(true, true));

        // then
        assertThat(order.getPreferredShippingAt()).isNull();
        verify(ordersRepository).save(order);
    }

    @Test
    @DisplayName("change rejects a date outside the window without saving")
    void changeRejectsDateOutsideWindow() {
        // given
        Order order = editableOrder();

        // when / then
        assertThatThrownBy(() -> service.change(order, ESTIMATED.minusDays(1), store(true, true)))
                .isInstanceOf(ClientPreferredShippingDateException.class)
                .extracting(e -> ((ClientPreferredShippingDateException) e).getReason())
                .isEqualTo(Reason.INVALID_DATE);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    @DisplayName("change rejects an order that is not editable")
    void changeRejectsNotEditable() {
        // given
        Order order = editableOrder();

        // when / then
        assertThatThrownBy(() -> service.change(order, ESTIMATED.plusDays(1), store(true, false)))
                .isInstanceOf(ClientPreferredShippingDateException.class)
                .extracting(e -> ((ClientPreferredShippingDateException) e).getReason())
                .isEqualTo(Reason.NOT_EDITABLE);
        verify(ordersRepository, never()).save(any());
    }

    private static Order editableOrder() {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setStatus(OrderStatus.Assembly);
        order.setEstimatedShippingAt(ESTIMATED);
        return order;
    }

    private static Store store(boolean pageEnabled, boolean preferredDateEnabled) {
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setClientOrderPageEnabled(pageEnabled);
        configuration.setClientPreferredShippingDateEnabled(preferredDateEnabled);
        store.setFulfilmentConfiguration(configuration);
        return store;
    }
}
