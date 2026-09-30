package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryListSortKeyTest {

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
        assertThat(DeliveryListSortKey.of(delivery)).isEqualTo("IN_TRANSIT#2026-10-01");
        assertThat(delivery.getDeliveryListSortKey()).isEqualTo("IN_TRANSIT#2026-10-01");
    }

    @Test
    void deliveryWithoutAPlannedDateSortsAfterEveryDatedOne() {
        // given
        Delivery delivery = delivery("Acme");

        // when / then
        assertThat(DeliveryListSortKey.of(delivery)).isEqualTo("IN_TRANSIT#9999-12-31");
        assertThat(DeliveryListSortKey.of(delivery)).isGreaterThan("IN_TRANSIT#2099-12-31");
    }

    @Test
    void receivedDeliveryWithoutAnInvoiceWaitsToBeSettled() {
        // given
        Delivery delivery = delivery("Acme");
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 15, 30));

        // when / then
        assertThat(DeliveryListSortKey.of(delivery)).isEqualTo("TO_SETTLE#2026-09-29T10:15:30");
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
        assertThat(delivery.getDeliveryListSortKey()).isEqualTo("SETTLED#2026-09-29T10:15:30");
    }

    @Test
    void theStoresOwnWarehouseNeverWaitsForAPurchaseInvoice() {
        // given
        Delivery delivery = delivery(SupplierRegistry.WAREHOUSE);
        delivery.setReceivedAt(LocalDateTime.of(2026, 9, 29, 10, 15, 30));

        // when / then
        assertThat(DeliveryListSortKey.of(delivery)).startsWith("SETTLED#");
    }

    @Test
    void receivedBoundsCoverTheWholeLastDay() {
        // when
        String lower = DeliveryListSortKey.receivedBound("TO_SETTLE#", LocalDate.of(2026, 9, 1), false);
        String upper = DeliveryListSortKey.receivedBound("TO_SETTLE#", LocalDate.of(2026, 9, 30), true);

        // then
        assertThat("TO_SETTLE#2026-09-01T00:00:00").isGreaterThanOrEqualTo(lower);
        assertThat("TO_SETTLE#2026-09-30T23:59:59.999").isLessThanOrEqualTo(upper);
        assertThat("TO_SETTLE#2026-10-01T00:00:00").isGreaterThan(upper);
    }
}
