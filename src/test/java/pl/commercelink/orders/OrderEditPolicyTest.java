package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEditPolicyTest {

    private static final OrderEditPolicy AFTER_WAREHOUSE = new OrderEditPolicy(true, false, false);
    private static final OrderEditPolicy AFTER_SALE = new OrderEditPolicy(false, true, false);
    private static final OrderEditPolicy AFTER_ADVANCE = new OrderEditPolicy(false, false, true);

    private static Order orderWith(DocumentType... types) {
        Order order = new Order("store-1");
        for (DocumentType type : types) {
            order.addDocument(new Document("id-" + type, type + "/1", null, type));
        }
        return order;
    }

    @Test
    void aStoreWithoutTheSettingsKeepsEveryDocumentLocking() {
        // given
        Store withoutConfiguration = new Store();
        Store withDefaults = new Store();
        withDefaults.setFulfilmentConfiguration(new FulfilmentConfiguration());

        // when / then
        assertThat(OrderEditPolicy.of(null)).isEqualTo(OrderEditPolicy.STRICT);
        assertThat(OrderEditPolicy.of(withoutConfiguration)).isEqualTo(OrderEditPolicy.STRICT);
        assertThat(OrderEditPolicy.of(withDefaults)).isEqualTo(OrderEditPolicy.STRICT);
    }

    @Test
    void thePolicyIsReadFromTheStoresFulfilmentConfiguration() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setOrderEditAfterWarehouseDocuments(true);
        configuration.setOrderEditAfterAdvanceDocuments(true);
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when
        OrderEditPolicy policy = OrderEditPolicy.of(store);

        // then
        assertThat(policy).isEqualTo(new OrderEditPolicy(true, false, true));
    }

    @Test
    void aSaleDocumentLocksUnlessTheStoreReleasedSaleDocuments() {
        // given
        Order invoiced = orderWith(DocumentType.InvoiceVat);

        // when / then
        assertThat(OrderEditPolicy.STRICT.saleDocumentLocks(invoiced)).isTrue();
        assertThat(AFTER_WAREHOUSE.saleDocumentLocks(invoiced)).isTrue();
        assertThat(AFTER_ADVANCE.saleDocumentLocks(invoiced)).isTrue();
        assertThat(AFTER_SALE.saleDocumentLocks(invoiced)).isFalse();
        assertThat(AFTER_SALE.invoiceLocks(invoiced)).isFalse();
        assertThat(OrderEditPolicy.STRICT.saleDocumentLocks(orderWith(DocumentType.InvoiceAdvance))).isFalse();
    }

    @Test
    void aGoodsIssueLocksUnlessTheStoreReleasedWarehouseDocuments() {
        // given
        Order issued = orderWith(DocumentType.GoodsIssue);

        // when / then
        assertThat(OrderEditPolicy.STRICT.goodsIssueLocks(issued)).isTrue();
        assertThat(AFTER_SALE.goodsIssueLocks(issued)).isTrue();
        assertThat(AFTER_WAREHOUSE.goodsIssueLocks(issued)).isFalse();
        assertThat(OrderEditPolicy.STRICT.goodsIssueLocks(orderWith())).isFalse();
    }

    @Test
    void pricesAreLockedByEveryDocumentWhoseGroupWasNotReleased() {
        // given
        Order withEveryGroup = orderWith(DocumentType.GoodsIssue, DocumentType.Receipt, DocumentType.InvoiceAdvance);

        // when / then
        assertThat(OrderEditPolicy.STRICT.priceLocks(orderWith())).isFalse();
        assertThat(AFTER_WAREHOUSE.priceLocks(orderWith(DocumentType.GoodsIssue, DocumentType.Reservation))).isFalse();
        assertThat(AFTER_SALE.priceLocks(orderWith(DocumentType.InvoiceFinal, DocumentType.InvoicePersonal))).isFalse();
        assertThat(AFTER_ADVANCE.priceLocks(orderWith(DocumentType.InvoiceAdvance, DocumentType.Order, DocumentType.Proforma))).isFalse();
        assertThat(AFTER_ADVANCE.priceLocks(orderWith(DocumentType.InvoiceVat))).isTrue();
        assertThat(AFTER_SALE.priceLocks(withEveryGroup)).isTrue();
        assertThat(new OrderEditPolicy(true, true, true).priceLocks(withEveryGroup)).isFalse();
    }

    @Test
    void theStoresSettingsApplyToItsAdminOnly() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setOrderEditAfterSaleDocuments(true);
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when / then
        assertThat(OrderEditPolicy.of(store, true)).isEqualTo(AFTER_SALE);
        assertThat(OrderEditPolicy.of(store, false)).isEqualTo(OrderEditPolicy.STRICT);
    }

    @Test
    void aFulfilledItemIsEditedInFullOnceAGroupIsReleasedAndNoDocumentStillLocks() {
        // given
        Order withoutDocuments = orderWith();
        Order invoiced = orderWith(DocumentType.InvoiceVat);
        Order invoicedAndIssued = orderWith(DocumentType.InvoiceVat, DocumentType.GoodsIssue);

        // when / then
        assertThat(OrderEditPolicy.STRICT.editsItemsInAnyStatus(withoutDocuments)).isFalse();
        assertThat(AFTER_SALE.editsItemsInAnyStatus(withoutDocuments)).isTrue();
        assertThat(AFTER_SALE.editsItemsInAnyStatus(invoiced)).isTrue();
        assertThat(AFTER_WAREHOUSE.editsItemsInAnyStatus(invoiced)).isFalse();
        assertThat(AFTER_SALE.editsItemsInAnyStatus(invoicedAndIssued)).isFalse();
        assertThat(new OrderEditPolicy(true, true, false).editsItemsInAnyStatus(invoicedAndIssued)).isTrue();
    }

    @Test
    void aReleasedDocumentNoLongerStopsSplittingTheOrder() {
        // given
        Order issuedAndInvoiced = orderWith(DocumentType.GoodsIssue, DocumentType.InvoiceVat);

        // when / then
        assertThat(issuedAndInvoiced.canBeSplit()).isFalse();
        assertThat(issuedAndInvoiced.canBeSplit(AFTER_SALE)).isFalse();
        assertThat(issuedAndInvoiced.canBeSplit(new OrderEditPolicy(true, true, false))).isTrue();
    }
}
