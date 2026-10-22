package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServicesScopeTest {

    private static final String MPN = "MPN-S1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO = LocalDate.of(2026, 5, 31);

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;
    @Mock private pl.commercelink.inventory.deliveries.DeliveriesRepository deliveriesRepository;

    private final PimCatalog pimCatalog = ScopedPimCatalog.of(
            ScopedPimCatalog.storeEntry("s1-2", "s1", ScopedPimCatalog.STORE_GTIN, MPN, "Monitors"));

    private void givenReceiptOfTheProduct() {
        WarehouseDocument doc = new WarehouseDocument();
        doc.setDocumentId("doc-1");
        doc.setType(DocumentType.GoodsReceipt);
        doc.setReason(DocumentReason.SupplierDelivery);
        doc.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        CounterpartyDetails counterparty = new CounterpartyDetails();
        counterparty.setCountry("Germany");
        counterparty.setCompanyName("Acme");
        doc.setCounterparty(counterparty);
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(List.of(doc));
        WarehouseDocumentItem item = new WarehouseDocumentItem();
        item.setDocumentId("doc-1");
        item.setItemId("item-1");
        item.setEan(ScopedPimCatalog.STORE_GTIN);
        item.setMfn(MPN);
        item.setName("Monitor");
        item.setQty(3);
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item));
    }

    @Test
    void storeEntryWeightsAreUsedForTheOwningStoreOnly() {
        // given
        givenReceiptOfTheProduct();
        ProductWeightOriginComplianceReportService service =
                new ProductWeightOriginComplianceReportService(documentRepository, itemRepository, pimCatalog);

        // when
        List<ProductWeightOriginComplianceReportRow> owner = service.generate("s1", FROM, TO);
        List<ProductWeightOriginComplianceReportRow> other = service.generate("s2", FROM, TO);

        // then
        assertThat(owner).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("Monitors");
            assertThat(row.weightNetG()).isEqualTo(2000);
            assertThat(row.totalWeightNetG()).isEqualTo(6000L);
        });
        assertThat(other).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("Unknown");
            assertThat(row.weightNetG()).isNull();
        });
    }

    @Test
    void storeEntryCategoryIsUsedByThePurchaseReportForTheOwningStoreOnly() {
        // given
        givenReceiptOfTheProduct();
        PurchaseReportService service =
                new PurchaseReportService(documentRepository, itemRepository, deliveriesRepository, pimCatalog);

        // when
        List<PurchaseReportRow> owner = service.generate("s1", FROM, TO);
        List<PurchaseReportRow> other = service.generate("s2", FROM, TO);

        // then
        assertThat(owner).singleElement().extracting(PurchaseReportRow::category).isEqualTo("Monitors");
        assertThat(other).singleElement().extracting(PurchaseReportRow::category).isEqualTo("Unknown");
    }
}
