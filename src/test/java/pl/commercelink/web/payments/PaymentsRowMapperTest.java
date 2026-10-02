package pl.commercelink.web.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.payments.PaymentEntriesTest.*;

class PaymentsRowMapperTest {

    PaymentsRowMapper mapper;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        SupplierLabelMap labels = mock(SupplierLabelMap.class);
        when(labels.of(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(labels.has(anyString(), anyString())).thenReturn(true);
        mapper = new PaymentsRowMapper(messages, new Locale("pl"), labels);
    }

    @Test
    void overduePayableShowsDaysAndGrossWithNet() {
        // given
        Delivery d = delivery(3380, TODAY.minusDays(17), 14);
        d.setTax(1.23);
        d.setType(DeliveryType.WAREHOUSE);

        // when
        PayableRow row = mapper.map(PayableEntry.of(d).orElseThrow(), TODAY);

        // then
        assertThat(row.dueText()).isEqualTo("28.09");
        assertThat(row.dueNote()).isEqualTo("po terminie: 3 dni");
        assertThat(row.dueTone()).isEqualTo("is-bad");
        assertThat(row.stateLabel()).isEqualTo("Nieopłacona");
        assertThat(row.amountText()).isEqualTo("4\u00A0157,40\u00A0PLN");
        assertThat(row.subText()).isEqualTo("netto 3\u00A0380,00\u00A0PLN");
        assertThat(row.orderedText()).isEqualTo("zam. 14.09");
        assertThat(row.externalText()).isEqualTo("nr ZS/1");
        assertThat(row.expected()).isEqualTo("4157.40");
        assertThat(row.actionLabel()).isEqualTo("Wpłata");
    }

    @Test
    void partialPaymentShowsWhatWasPaidOfTheTotal() {
        // given
        Delivery d = delivery(798.27, TODAY, 14);
        d.addPayment(paid(500));

        // when
        PayableRow row = mapper.map(PayableEntry.of(d).orElseThrow(), TODAY);

        // then
        assertThat(row.stateLabel()).isEqualTo("Niedopłata");
        assertThat(row.stateTone()).isEqualTo("is-warn");
        assertThat(row.subText()).isEqualTo("wpłacono 500,00 z 798,27");
        assertThat(row.paidOfLine()).isTrue();
    }

    @Test
    void overpaidPayableIsARefundWithoutMinus() {
        // given
        Delivery d = delivery(3120, TODAY.minusDays(26), 14);
        d.addPayment(paid(3240));

        // when
        PayableRow row = mapper.map(PayableEntry.of(d).orElseThrow(), TODAY);

        // then
        assertThat(row.stateLabel()).isEqualTo("Do zwrotu od dostawcy");
        assertThat(row.amountText()).isEqualTo("120,00\u00A0PLN");
        assertThat(row.dueNote()).as("a refund is not overdue").isNull();
        assertThat(row.refund()).isTrue();
        assertThat(row.expected()).as("a delivery refund is typed negative in the dialog").isEqualTo("-120.00");
        assertThat(row.actionLabel()).isEqualTo("Zwrot");
    }

    @Test
    void amountsNeverBreakInsideTheNumberOrBeforeTheCurrency() {
        // when
        String money = mapper.money(1234567.5);

        // then
        assertThat(money).isEqualTo("1\u00A0234\u00A0567,50\u00A0PLN").doesNotContain(" ");
    }

    @Test
    void payableWithoutOrderDateSaysSo() {
        // when
        PayableRow row = mapper.map(PayableEntry.of(delivery(100, null, 0)).orElseThrow(), TODAY);

        // then
        assertThat(row.dueText()).isEqualTo("bez terminu");
        assertThat(row.orderedText()).isEqualTo("zam. —");
    }

    @Test
    void receivableShowsMethodLabelAndShippingNote() {
        // given
        Order o = order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0);

        // when
        ReceivableRow row = mapper.map(ReceivableEntry.of(o, TODAY).orElseThrow(), TODAY);

        // then
        assertThat(row.methodLabel()).isEqualTo("Przelew bankowy");
        assertThat(row.shipText()).isEqualTo("wysłane");
        assertThat(row.shipNote()).isEqualTo("wysłane bez zapłaty");
        assertThat(row.shipTone()).isEqualTo("is-bad");
        assertThat(row.stateLabel()).isEqualTo("Nieopłacone");
        assertThat(row.number()).isEqualTo("b71e0a93");
        assertThat(row.href()).isEqualTo("/dashboard/orders/b71e0a93-0000-0000-0000-000000000000");
    }

    @Test
    void cashOnDeliveryInTransitIsCalm() {
        // given
        Order o = order(459, OrderStatus.Realization, PaymentSource.CashOnDelivery, 0);
        o.setEstimatedShippingAt(TODAY.plusDays(5));

        // when
        ReceivableRow row = mapper.map(ReceivableEntry.of(o, TODAY).orElseThrow(), TODAY);

        // then
        assertThat(row.stateLabel()).isEqualTo("Pobranie");
        assertThat(row.stateTone()).isEqualTo("is-neutral");
        assertThat(row.shipNote()).isNull();
        assertThat(row.shipText()).isEqualTo("06.10");
    }

    @Test
    void shippedOrderShowsTheShipmentDateAndWarnsAboutMissingPayment() {
        // given
        Order o = order(3499, OrderStatus.Realization, PaymentSource.BankTransfer, 0);
        Shipment shipment = new Shipment();
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 29, 10, 0));
        o.setShipments(new ArrayList<>(List.of(shipment)));

        // when
        ReceivableRow row = mapper.map(ReceivableEntry.of(o, TODAY).orElseThrow(), TODAY);

        // then
        assertThat(row.shipText()).isEqualTo("wysłane 29.09");
        assertThat(row.shipNote()).isEqualTo("wysłane bez zapłaty");
        assertThat(row.shipTone()).isEqualTo("is-bad");
    }

    @Test
    void deliveredOrderShowsTheDeliveryDateNotTheShippingDate() {
        // given
        Order o = order(1000, OrderStatus.Delivered, PaymentSource.CashOnDelivery, 0);
        Shipment shipment = new Shipment();
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 26, 10, 0));
        shipment.setDeliveredAt(LocalDateTime.of(2026, 9, 28, 15, 0));
        o.setShipments(new ArrayList<>(List.of(shipment)));

        // when
        ReceivableRow row = mapper.map(ReceivableEntry.of(o, TODAY).orElseThrow(), TODAY);

        // then
        assertThat(row.shipText()).isEqualTo("doręczone 28.09");
        assertThat(row.shipNote()).isEqualTo("pobranie nierozliczone");
    }

    @Test
    void deliveredOrderWithoutARecordedDeliveryDateIsNotGivenTheShippingDate() {
        // given
        Order o = order(1000, OrderStatus.Delivered, PaymentSource.BankTransfer, 0);
        Shipment shipment = new Shipment();
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 26, 10, 0));
        o.setShipments(new ArrayList<>(List.of(shipment)));

        // when
        ReceivableRow row = mapper.map(ReceivableEntry.of(o, TODAY).orElseThrow(), TODAY);

        // then
        assertThat(row.shipText()).isEqualTo("doręczone");
    }

    @Test
    void deliveredOrderWithoutShipmentsSaysDelivered() {
        // given
        Order o = order(1000, OrderStatus.Delivered, PaymentSource.BankTransfer, 0);

        // when
        ReceivableRow row = mapper.map(ReceivableEntry.of(o, TODAY).orElseThrow(), TODAY);

        // then
        assertThat(row.shipText()).isEqualTo("doręczone");
        assertThat(row.shipNote()).isEqualTo("wysłane bez zapłaty");
    }
}
