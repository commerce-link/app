package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.builtin.CounterpartyDetails;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.DocumentRow;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRowMapperTest {

    private final MessageSource messages = TestMessages.polish(); // see note below
    private final DocumentRowMapper mapper = new DocumentRowMapper(messages, Locale.forLanguageTag("pl"), false, "/dashboard");

    @Test
    void receiptRowNamesTypeReasonSourceCounterpartyAndAuthor() {
        // given
        WarehouseDocument d = document("doc-1", "PZ/MAG1/2026/000214", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery);
        d.setDeliveryId("3f2a9c1e-0000-4000-8000-000000000000");
        CounterpartyDetails cp = new CounterpartyDetails();
        cp.setCompanyName("AB S.A.");
        d.setCounterparty(cp);
        d.setCreatedAt(LocalDateTime.of(2026, 10, 7, 14, 32));
        d.setCreatedBy("Jan Kowalski");

        // when
        DocumentRow row = mapper.row(d);

        // then
        assertThat(row.href()).isEqualTo("/dashboard/warehouse-documents/details?documentId=doc-1");
        assertThat(row.number()).isEqualTo("PZ/MAG1/2026/000214");
        assertThat(row.typeName()).isEqualTo("Przyjęcie zewnętrzne");
        assertThat(row.incoming()).isTrue();
        assertThat(row.reason()).isEqualTo("Dostawa od dostawcy");
        assertThat(row.source()).isEqualTo("Dostawa 3f2a9c1e");
        assertThat(row.counterparty()).isEqualTo("AB S.A.");
        assertThat(row.date()).isEqualTo("07.10.2026");
        assertThat(row.timeAndAuthor()).isEqualTo("14:32 · Jan Kowalski");
    }

    @Test
    void returnWinsOverOrderAndPersonNameIsUsedWithoutCompany() {
        // given
        WarehouseDocument d = document("doc-2", "PZ/MAG1/2026/000213", DocumentType.GoodsReceipt, DocumentReason.CustomerReturn);
        d.setOrderId("8c1d02aa-0000-4000-8000-000000000000");
        d.setRmaId("5521af00-0000-4000-8000-000000000000");
        CounterpartyDetails cp = new CounterpartyDetails();
        cp.setName("Tomasz");
        cp.setSurname("Zieliński");
        d.setCounterparty(cp);

        // when
        DocumentRow row = mapper.row(d);

        // then
        assertThat(row.source()).isEqualTo("Zwrot 5521af00");
        assertThat(row.counterparty()).isEqualTo("Tomasz Zieliński");
    }

    @Test
    void internalIssueWithoutCounterpartyShowsTheNoteAndNoSource() {
        // given
        WarehouseDocument d = document("doc-3", "RW/MAG1/2026/000031", DocumentType.InternalIssue, DocumentReason.Destruction);
        d.setNote("Uszkodzony w transporcie");
        d.setCreatedBy(null);

        // when
        DocumentRow row = mapper.row(d);

        // then
        assertThat(row.incoming()).isFalse();
        assertThat(row.note()).isEqualTo("Uszkodzony w transporcie");
        assertThat(row.source()).isNull();
        assertThat(row.counterparty()).isNull();
        assertThat(row.timeAndAuthor()).endsWith("System");
    }

    @Test
    void superAdminLinksTheStorePathAndSeesTheStore() {
        // given
        DocumentRowMapper sa = new DocumentRowMapper(messages, Locale.forLanguageTag("pl"), true, "/dashboard/store/s1");

        // when
        DocumentRow row = sa.row(document("doc-1", "PZ/MAG1/2026/000214", DocumentType.GoodsReceipt, null));

        // then
        assertThat(row.href()).isEqualTo("/dashboard/store/s1/warehouse-documents/details?documentId=doc-1");
        assertThat(row.storeId()).isEqualTo("s1");
        assertThat(row.reason()).isNull();
    }

    @Test
    void documentWithoutTypeStillRenders() {
        // given
        WarehouseDocument d = document("doc-9", "XX/MAG1/2026/000001", null, null);

        // when
        DocumentRow row = mapper.row(d);

        // then
        assertThat(row.typeName()).isEqualTo("—");
        assertThat(row.incoming()).isFalse();
    }

    private static WarehouseDocument document(String id, String no, DocumentType type, DocumentReason reason) {
        WarehouseDocument d = new WarehouseDocument();
        d.setStoreId("s1");
        d.setDocumentId(id);
        d.setDocumentNo(no);
        d.setType(type);
        d.setReason(reason);
        d.setCreatedAt(LocalDateTime.of(2026, 10, 7, 9, 0));
        return d;
    }

    @Test
    void shortIdKeepsTheFirstSegmentAsStoredLikeTheDeliveryScreen() {
        assertThat(DocumentRowMapper.shortId("3F2A9C1E-0000-4000-8000-000000000000")).isEqualTo("3F2A9C1E");
        assertThat(DocumentRowMapper.shortId("legacy-id")).isEqualTo("legacy-id");
    }
}
