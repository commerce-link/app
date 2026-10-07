package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListQuery.SearchMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseDocumentListQueryTest {

    private static final String PATH = "/dashboard/warehouse-documents";

    private static MultiValueMap<String, String> params(String... pairs) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.add(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void parsesKindReasonsDatesSearchAndPage() {
        // when
        WarehouseDocumentListQuery q = WarehouseDocumentListQuery.parse(PATH, params(
                "type", "RW", "reason", "Destruction", "reason", "Theft", "from", "2026-10-01", "to", "2026-10-07",
                "q", "  000031 ", "page", "2"));

        // then
        assertThat(q.kind()).isEqualTo(DocumentKind.RW);
        assertThat(q.reasons()).containsExactly(DocumentReason.Destruction, DocumentReason.Theft);
        assertThat(q.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(q.to()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(q.q()).isEqualTo("000031");
        assertThat(q.page()).isEqualTo(2);
        assertThat(q.href()).isEqualTo(PATH + "?type=RW&reason=Destruction&reason=Theft&from=2026-10-01&to=2026-10-07&q=000031&page=2");
    }

    @Test
    void unknownTypeAndForeignReasonsAreIgnoredNeverAnError() {
        // when
        WarehouseDocumentListQuery q = WarehouseDocumentListQuery.parse(PATH, params(
                "type", "MM", "reason", "nonsense", "page", "x"));
        WarehouseDocumentListQuery pz = WarehouseDocumentListQuery.parse(PATH, params("type", "PZ", "reason", "Theft"));

        // then
        assertThat(q.kind()).isNull();
        assertThat(q.reasons()).isEmpty();
        assertThat(q.page()).isEqualTo(1);
        assertThat(pz.reasons()).as("Theft is not a PZ reason").isEmpty();
    }

    @Test
    void reversedDatesAreSwapped() {
        // when
        WarehouseDocumentListQuery q = WarehouseDocumentListQuery.parse(PATH, params("from", "2026-10-07", "to", "2026-10-01"));

        // then
        assertThat(q.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(q.to()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void legacyParametersRedirectToTheNewQuery() {
        // when
        Optional<String> target = WarehouseDocumentListQuery.legacyRedirect(PATH, params(
                "type", "GoodsReceipt", "dateFrom", "2026-08-01", "dateTo", "", "warehouseId", "", "ean", "5901234123457", "mfn", ""));
        Optional<String> warehouse = WarehouseDocumentListQuery.legacyRedirect(PATH, params("warehouseId", "MAG1"));
        Optional<String> modern = WarehouseDocumentListQuery.legacyRedirect(PATH, params("type", "PZ", "q", "x"));

        // then
        assertThat(target).contains(PATH + "?type=PZ&from=2026-08-01&q=5901234123457");
        assertThat(warehouse).contains(PATH + "?q=%2FMAG1%2F");
        assertThat(modern).isEmpty();
    }

    @Test
    void searchModeTreatsSlashAsDocumentNumber() {
        // when / then
        assertThat(query("PZ/MAG1/2026").searchMode()).isEqualTo(SearchMode.NUMBER);
        assertThat(query("000214").searchMode()).isEqualTo(SearchMode.NUMBER);
        assertThat(query("pz/mag1").numberFragments(null)).containsExactly("pz/mag1", "PZ/MAG1");
        assertThat(query("5901234123457").searchMode()).isEqualTo(SearchMode.PRODUCT);
        assertThat(query("910-006559").searchMode()).isEqualTo(SearchMode.PRODUCT);
        assertThat(query("910-006559").productCode()).isEqualTo("910-006559");
        assertThat(query("910-006559").numberFragments("MAG1")).isEmpty();
        assertThat(query(null).searchMode()).isNull();
    }

    @Test
    void numberFragmentsKeepTheTypedFormAndItsUpperCaseOnlyWhenTheyDiffer() {
        // when / then
        assertThat(query("PZ/MAG-uma2dqukxr/2026/000214").numberFragments(null))
                .containsExactly("PZ/MAG-uma2dqukxr/2026/000214", "PZ/MAG-UMA2DQUKXR/2026/000214");
        assertThat(query("PZ/MAG1/2026").numberFragments("MAG1")).containsExactly("PZ/MAG1/2026");
        assertThat(query("000214").numberFragments("MAG-uma2dqukxr")).containsExactly("000214");
    }

    @Test
    void numberFragmentsRestoreTheStoredCasingOfTheWarehouseId() {
        // given
        String warehouseId = "MAG-uma2dqukxr";

        // when / then
        assertThat(query("pz/mag-uma2dqukxr/2026/000214").numberFragments(warehouseId))
                .contains("PZ/MAG-uma2dqukxr/2026/000214");
        assertThat(query("Pz/MAG-uma2dqukxr/2026/000214").numberFragments(warehouseId))
                .contains("PZ/MAG-uma2dqukxr/2026/000214");
        assertThat(query("MAG-UMA2DQUKXR/2026/0002").numberFragments(warehouseId))
                .contains("MAG-uma2dqukxr/2026/0002");
        assertThat(query("pz/mag-uma2").numberFragments(warehouseId))
                .containsExactly("pz/mag-uma2", "PZ/MAG-UMA2", "PZ/MAG-uma2");
    }

    @Test
    void warehouseIdCasingIsRestoredInWholeSegmentsTheLastSegmentPrefixAndTheFirstSegmentSuffix() {
        // when / then
        assertThat(WarehouseDocumentListQuery.withWarehouseIdCase("/mag-uma2dqukxr/", "MAG-uma2dqukxr")).isEqualTo("/MAG-uma2dqukxr/");
        assertThat(WarehouseDocumentListQuery.withWarehouseIdCase("pz/mag-u", "MAG-uma2dqukxr")).isEqualTo("PZ/MAG-u");
        assertThat(WarehouseDocumentListQuery.withWarehouseIdCase("dqukxr/2026", "MAG-uma2dqukxr")).isEqualTo("dqukxr/2026");
        assertThat(WarehouseDocumentListQuery.withWarehouseIdCase("pz/2026/0002", "MAG-uma2dqukxr")).isEqualTo("PZ/2026/0002");
        assertThat(WarehouseDocumentListQuery.withWarehouseIdCase("pz/mag1", null)).isEqualTo("PZ/MAG1");
        assertThat(WarehouseDocumentListQuery.withWarehouseIdCase("pz/mag1", " ")).isEqualTo("PZ/MAG1");
    }

    @Test
    void aQueryWithoutSlashKeepsItsTwoVariantsWhateverTheWarehouseId() {
        // when / then
        assertThat(query("000214").numberFragments("000214x")).containsExactly("000214");
    }

    @Test
    void changingTheKindDropsReasonsItDoesNotHaveAndResetsThePage() {
        // given
        WarehouseDocumentListQuery q = WarehouseDocumentListQuery.parse(PATH, params(
                "type", "RW", "reason", "Destruction", "reason", "StockAdjustment", "page", "3"));

        // when
        WarehouseDocumentListQuery pw = q.withKind(DocumentKind.PW);

        // then
        assertThat(pw.reasons()).containsExactly(DocumentReason.StockAdjustment);
        assertThat(pw.page()).isEqualTo(1);
        assertThat(q.withKind(null).reasons()).containsExactly(DocumentReason.Destruction, DocumentReason.StockAdjustment);
    }

    @Test
    void clearedKeepsOnlyTheKind() {
        // given
        WarehouseDocumentListQuery q = WarehouseDocumentListQuery.parse(PATH, params(
                "type", "WZ", "reason", "CustomerOrder", "from", "2026-10-01", "q", "x"));

        // when / then
        assertThat(q.cleared().href()).isEqualTo(PATH + "?type=WZ");
        assertThat(q.isFiltered()).isTrue();
        assertThat(q.cleared().isFiltered()).isFalse();
        assertThat(q.activeFilterCount()).isEqualTo(2);
    }

    @Test
    void superAdminPathIsCarriedInEveryLink() {
        // when
        WarehouseDocumentListQuery q = WarehouseDocumentListQuery.parse("/dashboard/store/s1/warehouse-documents", params("type", "PZ"));

        // then
        assertThat(q.withPage(2).href()).isEqualTo("/dashboard/store/s1/warehouse-documents?type=PZ&page=2");
    }

    private static WarehouseDocumentListQuery query(String q) {
        return new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, q, 1);
    }
}
