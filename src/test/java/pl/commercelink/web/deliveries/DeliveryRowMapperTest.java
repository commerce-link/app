package pl.commercelink.web.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryRowMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    @Mock
    private SupplierLabelMap labels;
    private ResourceBundleMessageSource messages;

    @BeforeEach
    void setUp() {
        messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        lenient().when(labels.of("store-1", "Acme")).thenReturn("Acme");
        lenient().when(labels.has("store-1", "Acme")).thenReturn(true);
    }

    private DeliveryRowMapper mapper(boolean superAdmin) {
        return new DeliveryRowMapper(messages, new Locale("pl"), labels, superAdmin);
    }

    private static Delivery delivery() {
        Delivery delivery = new Delivery();
        delivery.setStoreId("store-1");
        delivery.setDeliveryId("7a31c0e2-1111-2222-3333-444455556666");
        delivery.setProvider("Acme");
        delivery.setType(DeliveryType.WAREHOUSE);
        delivery.setOrderedAt(LocalDateTime.of(2026, 9, 24, 8, 0));
        delivery.setTotalCost(3380.00);
        return delivery;
    }

    @Test
    void overdueDeliveryOnItsWay() {
        // given
        Delivery delivery = delivery();
        delivery.setEstimatedDeliveryAt(LocalDate.of(2026, 9, 27));
        delivery.setExternalDeliveryId("ZS/104733/2026");
        delivery.setCounterpartyShortcut("EuSarl");

        // when
        DeliveryRow row = mapper(false).map(delivery, TODAY);

        // then
        assertThat(row.href()).isEqualTo("/dashboard/deliveries/details?deliveryId=7a31c0e2-1111-2222-3333-444455556666");
        assertThat(row.number()).isEqualTo("7a31c0e2");
        assertThat(row.storeId()).isNull();
        assertThat(row.dateText()).isEqualTo("27.09");
        assertThat(row.dueNote()).isEqualTo("po terminie: 3 dni");
        assertThat(row.dueTone()).isEqualTo("is-bad");
        assertThat(row.stateLabel()).isEqualTo("W drodze");
        assertThat(row.stateTone()).isEqualTo("is-info");
        assertThat(row.counterparty()).isEqualTo("EuSarl");
        assertThat(row.grossText()).isEqualTo("4 157,40 PLN");
        assertThat(row.netText()).isEqualTo("netto 3 380,00 PLN");
        assertThat(row.marks()).isEmpty();
    }

    @Test
    void receivedDeliveryShowsWhatSettlementStillLacks() {
        // given
        Delivery delivery = delivery();
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 0));

        // when
        DeliveryRow row = mapper(false).map(delivery, TODAY);

        // then
        assertThat(row.stateLabel()).isEqualTo("Odebrana");
        assertThat(row.dateLabelKey()).isEqualTo("deliveries.list.column.received");
        assertThat(row.dueNote()).isNull();
        assertThat(row.marks()).extracting(DeliveryRow.Mark::code).containsExactly("FV");
        assertThat(row.hasTodo()).isTrue();
    }

    @Test
    void superAdminLinksThroughTheStore() {
        // when
        DeliveryRow row = mapper(true).map(delivery(), TODAY);

        // then
        assertThat(row.href()).isEqualTo("/dashboard/store/store-1/deliveries/details?deliveryId=7a31c0e2-1111-2222-3333-444455556666");
        assertThat(row.storeId()).isEqualTo("store-1");
    }

    @Test
    void supplierTypedByHandIsMarkedAsSuch() {
        // given
        Delivery delivery = delivery();
        delivery.setProvider("Hurtownia Kowalski");
        when(labels.of("store-1", "Hurtownia Kowalski")).thenReturn("Hurtownia Kowalski");

        // when / then
        assertThat(mapper(false).map(delivery, TODAY).supplierLabel()).isEqualTo("Inny: Hurtownia Kowalski");
    }
}
