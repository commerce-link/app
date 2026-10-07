package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.stores.Printer;
import pl.commercelink.warehouse.builtin.CounterpartyDetails;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.ItemLine;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.Link;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseDocumentPageMapperTest {

    private final WarehouseDocumentPageMapper mapper =
            new WarehouseDocumentPageMapper(TestMessages.polish(), Locale.forLanguageTag("pl"));

    @Test
    void headerNamesTheNumberTypeReasonDateAuthorAndNetTotal() {
        // when
        WarehouseDocumentPage page = mapper.page(receipt(), items(), List.of(), false);

        // then
        assertThat(page.number()).isEqualTo("PZ/MAG1/2026/000214");
        assertThat(page.typeName()).isEqualTo("Przyjęcie zewnętrzne");
        assertThat(page.reason()).isEqualTo("Dostawa od dostawcy");
        assertThat(page.createdText()).isEqualTo("Utworzono 07.10.2026, 14:32");
        assertThat(page.author()).isEqualTo("Jan Kowalski");
        assertThat(page.totalNet()).isEqualTo(money(3494.0));
        assertThat(page.backHref()).isEqualTo("/dashboard/warehouse-documents");
    }

    @Test
    void itemsAreSortedByNameWithValueAndSummary() {
        // when
        WarehouseDocumentPage page = mapper.page(receipt(), items(), List.of(), false);

        // then
        assertThat(page.items()).extracting(ItemLine::name).containsExactly("Kabel HDMI", "Samsung 990 PRO");
        assertThat(page.items().get(1).value()).isEqualTo(money(3445.0));
        assertThat(page.itemsTitle()).isEqualTo("Pozycje: 2");
        assertThat(page.summaryQty()).isEqualTo("Razem: 6 szt.");
        assertThat(page.summaryTotal()).isEqualTo(money(3494.0) + " netto");
    }

    @Test
    void itemsAreSortedByPolishAlphabetSoPlytaComesBeforeProcesor() {
        // given
        WarehouseDocumentItem cpu = item("Procesor");
        WarehouseDocumentItem board = item("Płyta");
        WarehouseDocumentItem cable = item("kabel");

        // when
        WarehouseDocumentPage page = mapper.page(receipt(), List.of(cpu, board, cable), List.of(), false);

        // then
        assertThat(page.items()).extracting(ItemLine::name).containsExactly("kabel", "Płyta", "Procesor");
    }

    @Test
    void labelsAreCountedLikeThePrintServicePrintsThemAtLeastOnePerItem() {
        // given
        WarehouseDocumentItem noQty = item("Bez ilości");
        noQty.setQty(0);
        WarehouseDocumentItem three = item("Trzy");
        three.setQty(3);

        // when
        WarehouseDocumentPage page = mapper.page(receipt(), List.of(noQty, three), List.of(printer()), false);

        // then
        assertThat(page.print().labelsText()).isEqualTo("Etykiety: 4");
        assertThat(page.summaryQty()).isEqualTo("Razem: 3 szt.");
    }

    @Test
    void deliveryLinkEncodesTheDeliveryId() {
        // given
        WarehouseDocument d = receipt();
        d.setDeliveryId("del 1&x=2");

        // when
        WarehouseDocumentPage page = mapper.page(d, items(), List.of(), false);

        // then
        assertThat(page.links().get(0).href()).isEqualTo("/dashboard/deliveries/details?deliveryId=del+1%26x%3D2");
    }

    @Test
    void itemWithDeliveryAndMfnLinksItsHistoryForAStoreUserOnly() {
        // when
        WarehouseDocumentPage user = mapper.page(receipt(), items(), List.of(), false);
        WarehouseDocumentPage superAdmin = mapper.page(receipt(), items(), List.of(), true);

        // then
        assertThat(user.items().get(1).historyHref())
                .isEqualTo("/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=del-1&mfn=MZ-V9P2T0BW&from=document&documentId=doc-1");
        assertThat(user.anyItemMenu()).isTrue();
        assertThat(superAdmin.anyItemMenu()).isFalse();
    }

    @Test
    void sideCardsLinkDeliveryOrderAndRmaAndBuildAddressesWithoutNulls() {
        // given
        WarehouseDocument d = receipt();
        d.setOrderId("ord-1");
        d.setRmaId("rma-1");
        CounterpartyDetails cp = new CounterpartyDetails();
        cp.setCompanyName("AB S.A.");
        cp.setCity("Wrocław");
        cp.setTaxId("8951628108");
        d.setCounterparty(cp);

        // when
        WarehouseDocumentPage page = mapper.page(d, items(), List.of(), false);

        // then
        assertThat(page.links()).extracting(Link::label).containsExactly("Dostawa", "Zamówienie", "Zwrot (RMA)", "Magazyn");
        assertThat(page.links()).extracting(Link::href).containsExactly(
                "/dashboard/deliveries/details?deliveryId=del-1", "/dashboard/orders/ord-1", "/dashboard/rma/rma-1", null);
        assertThat(page.counterparty().lines()).containsExactly("Wrocław");
        assertThat(page.counterparty().taxId()).isEqualTo("NIP 8951628108");
    }

    @Test
    void superAdminGetsStoreScopedDeliveryAndOrderLinksTheRmaAsTextAndTheStoreBack() {
        // given
        WarehouseDocument d = receipt();
        d.setOrderId("ord-1");
        d.setRmaId("rma-1");

        // when
        WarehouseDocumentPage page = mapper.page(d, items(), List.of(printer()), true);

        // then
        assertThat(page.backHref()).isEqualTo("/dashboard/store/s1/warehouse-documents");
        assertThat(page.links()).extracting(Link::label).containsExactly("Dostawa", "Zamówienie", "Zwrot (RMA)", "Magazyn");
        assertThat(page.links()).extracting(Link::href).containsExactly(
                "/dashboard/store/s1/deliveries/details?deliveryId=del-1", "/dashboard/store/s1/orders/ord-1", null, null);
        assertThat(page.print()).isNull();
    }

    @Test
    void printIsOfferedOnAReceiptWithPrintersAndCountsLabels() {
        // when
        WarehouseDocumentPage page = mapper.page(receipt(), items(), List.of(printer()), false);
        WarehouseDocumentPage issue = mapper.page(issue(), items(), List.of(printer()), false);
        WarehouseDocumentPage noPrinters = mapper.page(receipt(), items(), List.of(), false);

        // then
        assertThat(page.print().labelsText()).isEqualTo("Etykiety: 6");
        assertThat(page.print().labels()).isEqualTo(6);
        assertThat(page.print().printers()).extracting(WarehouseDocumentPage.Printer::name).containsExactly("Zebra ZD421");
        assertThat(page.print().printers().get(0).deviceId()).isEqualTo("uid-1");
        assertThat(page.print().endpoint()).isEqualTo("/dashboard/warehouse-documents/print-labels");
        assertThat(issue.print()).isNull();
        assertThat(noPrinters.print()).isNull();
    }

    @Test
    void receiptWithoutItemsOffersNoPrint() {
        // when
        WarehouseDocumentPage page = mapper.page(receipt(), List.of(), List.of(printer()), false);

        // then
        assertThat(page.items()).isEmpty();
        assertThat(page.itemsTitle()).isEqualTo("Pozycje: 0");
        assertThat(page.print()).isNull();
    }

    @Test
    void printerWithoutSettingsHasNoDeviceIdInsteadOfFailing() {
        // given
        Printer bare = new Printer();
        bare.setName("Bare");
        bare.setSettings(null);

        // when
        WarehouseDocumentPage page = mapper.page(receipt(), items(), List.of(bare), false);

        // then
        assertThat(page.print().printers()).hasSize(1);
        assertThat(page.print().printers().get(0).deviceId()).isNull();
    }

    @Test
    void internalDocumentWithoutCounterpartyHasDetailsCardWithTheNote() {
        // given
        WarehouseDocument d = issue();
        d.setType(DocumentType.InternalIssue);
        d.setDeliveryId(null);
        d.setNote("Uszkodzony w transporcie");

        // when
        WarehouseDocumentPage page = mapper.page(d, items(), List.of(), false);

        // then
        assertThat(page.linksTitle()).isEqualTo("Szczegóły");
        assertThat(page.links()).extracting(Link::label).containsExactly("Magazyn", "Notatka");
        assertThat(page.links().get(1).wide()).isTrue();
        assertThat(page.counterparty()).isNull();
    }

    @Test
    void documentWithoutTypeIsToleratedLikeInTheList() {
        // given
        WarehouseDocument d = receipt();
        d.setType(null);

        // when
        WarehouseDocumentPage page = mapper.page(d, items(), List.of(printer()), false);

        // then
        assertThat(page.typeName()).isEqualTo("—");
        assertThat(page.incoming()).isFalse();
        assertThat(page.print()).isNull();
    }

    private static String money(double amount) {
        return Money.format(amount) + " PLN";
    }

    private static WarehouseDocument receipt() {
        WarehouseDocument d = new WarehouseDocument();
        d.setDocumentId("doc-1");
        d.setStoreId("s1");
        d.setDocumentNo("PZ/MAG1/2026/000214");
        d.setType(DocumentType.GoodsReceipt);
        d.setReason(DocumentReason.SupplierDelivery);
        d.setDeliveryId("del-1");
        d.setWarehouseId("MAG1");
        d.setCreatedAt(LocalDateTime.of(2026, 10, 7, 14, 32));
        d.setCreatedBy("Jan Kowalski");
        return d;
    }

    private static WarehouseDocument issue() {
        WarehouseDocument d = receipt();
        d.setType(DocumentType.GoodsIssue);
        return d;
    }

    private static List<WarehouseDocumentItem> items() {
        WarehouseDocumentItem ssd = new WarehouseDocumentItem();
        ssd.setName("Samsung 990 PRO");
        ssd.setEan("8806094215038");
        ssd.setMfn("MZ-V9P2T0BW");
        ssd.setQty(5);
        ssd.setUnitPrice(689.0);
        ssd.setDeliveryId("del-1");
        WarehouseDocumentItem cable = new WarehouseDocumentItem();
        cable.setName("Kabel HDMI");
        cable.setMfn("HDMI21-2M");
        cable.setQty(1);
        cable.setUnitPrice(49.0);
        cable.setDeliveryId("del-1");
        return List.of(ssd, cable);
    }

    private static WarehouseDocumentItem item(String name) {
        WarehouseDocumentItem i = new WarehouseDocumentItem();
        i.setName(name);
        i.setQty(1);
        return i;
    }

    private static Printer printer() {
        Printer p = new Printer();
        p.setName("Zebra ZD421");
        p.setSettings(Map.of("deviceId", "uid-1"));
        return p;
    }
}
