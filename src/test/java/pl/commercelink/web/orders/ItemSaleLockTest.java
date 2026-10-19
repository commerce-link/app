package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSaleLockTest {

    private static OrderItem stored(FulfilmentStatus status) {
        OrderItem item = new OrderItem("o", "CPU", "Ryzen 7", 2, 749, "MFN-1", false);
        item.setTax(1.23);
        item.setStatus(status);
        return item;
    }

    /** What the item page posts: every field as the page shows it (read-only fields included). */
    private static OrderItem posted(OrderItem stored) {
        OrderItem posted = new OrderItem();
        posted.setName(stored.getName());
        posted.setQty(stored.getQty());
        posted.setTax(stored.getTax());
        posted.setSerialNo("SN-NEW");
        posted.setComment("changed comment");
        return posted;
    }

    @Test
    void anInvoicedOrderLocksWithTheInvoicedReasonsWhateverTheReceipt() {
        // given
        Order invoiced = new Order("store-1");
        invoiced.addDocument(new Document("r1", "PAR/1", null, DocumentType.Receipt));
        Order open = new Order("store-1");

        // when / then
        assertThat(ItemSaleLock.of(invoiced, false)).isEqualTo(ItemSaleLock.INVOICED);
        assertThat(ItemSaleLock.of(invoiced, true)).isEqualTo(ItemSaleLock.INVOICED);
        assertThat(ItemSaleLock.of(open, true)).isEqualTo(ItemSaleLock.RECEIPT_ISSUING);
        assertThat(ItemSaleLock.of(open, false)).isNull();
    }

    @Test
    void anAdvanceInvoiceAloneDoesNotLockTheSaleFields() {
        // given: not a closing document, so the order is not invoiced yet
        Order order = new Order("store-1");
        order.addDocument(new Document("a1", "FZ/1", null, DocumentType.InvoiceAdvance));

        // when / then
        assertThat(ItemSaleLock.of(order, false)).isNull();
    }

    @Test
    void unchangedSaleFieldsPassSoTheOtherFieldsStillSave() {
        // given
        OrderItem item = stored(FulfilmentStatus.New);
        OrderItem posted = posted(item);
        posted.setName("  Ryzen 7 ");

        // when / then
        assertThat(ItemSaleLock.changes(item, posted)).isFalse();
    }

    @Test
    void aChangedNameQuantityOrVatRateOfANewItemIsAChange() {
        // given
        OrderItem item = stored(FulfilmentStatus.New);
        OrderItem name = posted(item);
        name.setName("Ryzen 9");
        OrderItem qty = posted(item);
        qty.setQty(3);
        OrderItem tax = posted(item);
        tax.setTax(1.08);
        OrderItem noName = posted(item);
        noName.setName(null);

        // when / then
        assertThat(ItemSaleLock.changes(item, name)).isTrue();
        assertThat(ItemSaleLock.changes(item, qty)).isTrue();
        assertThat(ItemSaleLock.changes(item, tax)).isTrue();
        assertThat(ItemSaleLock.changes(item, noName)).isTrue();
    }

    @Test
    void aFulfilledItemIsJudgedOnlyOnTheFieldsItsUpdateApplies() {
        // given: its page does not post quantity or VAT (disabled), and OrderItem#update ignores them
        OrderItem item = stored(FulfilmentStatus.Delivered);
        OrderItem posted = new OrderItem();
        posted.setName("Ryzen 7");
        OrderItem renamed = new OrderItem();
        renamed.setName("Ryzen 9");

        // when / then
        assertThat(ItemSaleLock.changes(item, posted)).isFalse();
        assertThat(ItemSaleLock.changes(item, renamed)).isTrue();
    }

    @Test
    void keepPutsTheStoredSaleFieldsBackOnThePost() {
        // given
        OrderItem item = stored(FulfilmentStatus.New);
        OrderItem posted = posted(item);
        posted.setName(" Ryzen 7 ");

        // when
        ItemSaleLock.keep(item, posted);

        // then
        assertThat(posted.getName()).isEqualTo("Ryzen 7");
        assertThat(posted.getQty()).isEqualTo(2);
        assertThat(posted.getTax()).isEqualTo(1.23);
        assertThat(posted.getSerialNo()).isEqualTo("SN-NEW");
    }
}
