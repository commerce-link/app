package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryListKeyTest {

    private static Delivery delivery(String provider) {
        Delivery delivery = new Delivery();
        delivery.setStoreId("store-1");
        delivery.setDeliveryId("7a31c0e2-0000-0000-0000-000000000001");
        delivery.setProvider(provider);
        return delivery;
    }

    @Test
    void deliveryOnItsWayIsKeyedByItsPlannedDate() {
        // given
        Delivery delivery = delivery("Acme");
        delivery.setEstimatedDeliveryAt(LocalDate.of(2026, 10, 1));

        // when / then
        assertThat(DeliveryListKey.of(delivery)).isEqualTo("T#2026-10-01");
        assertThat(delivery.getListKey()).isEqualTo("T#2026-10-01");
    }

    @Test
    void deliveryWithoutAPlannedDateSortsAfterEveryDatedOne() {
        // given
        Delivery delivery = delivery("Acme");

        // when / then
        assertThat(DeliveryListKey.of(delivery)).isEqualTo("T#9999-12-31");
        assertThat(DeliveryListKey.of(delivery)).isGreaterThan("T#2099-12-31");
    }

    @Test
    void receivedDeliveryWithoutAnInvoiceWaitsToBeSettled() {
        // given
        Delivery delivery = delivery("Acme");
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 15, 30));

        // when / then
        assertThat(DeliveryListKey.of(delivery)).isEqualTo("S#2026-09-29T10:15:30");
    }

    @Test
    void attachingAnInvoiceMovesTheDeliveryToTheSettledHistory() {
        // given
        Delivery delivery = delivery("Acme");
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 15, 30));
        Document invoice = new Document();
        invoice.setType(DocumentType.InvoiceVat);

        // when
        delivery.addDocument(invoice);

        // then
        assertThat(delivery.getListKey()).isEqualTo("R#2026-09-29T10:15:30");
    }

    @Test
    void theStoresOwnWarehouseNeverWaitsForAPurchaseInvoice() {
        // given
        Delivery delivery = delivery(SupplierRegistry.WAREHOUSE);
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 15, 30));

        // when / then
        assertThat(DeliveryListKey.of(delivery)).startsWith("R#");
    }

    @Test
    void receivedBoundsCoverTheWholeLastDay() {
        // when
        String lower = DeliveryListKey.receivedBound("S#", LocalDate.of(2026, 9, 1), false);
        String upper = DeliveryListKey.receivedBound("S#", LocalDate.of(2026, 9, 30), true);

        // then
        assertThat("S#2026-09-01T00:00:00").isGreaterThanOrEqualTo(lower);
        assertThat("S#2026-09-30T23:59:59.999").isLessThanOrEqualTo(upper);
        assertThat("S#2026-10-01T00:00:00").isGreaterThan(upper);
    }
}
