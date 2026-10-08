package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.web.warehousedocuments.DocumentKind;
import pl.commercelink.web.warehousedocuments.TestMessages;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.Chip;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.Option;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.SegmentLink;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseDocumentListServiceTest {

    private static final String PATH = "/dashboard/warehouse-documents";
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private WarehouseDocumentSearchService search;
    @Mock
    private StoresRepository stores;

    private WarehouseDocumentListService service;

    @BeforeEach
    void setup() {
        service = new WarehouseDocumentListService(search, stores, TestMessages.polish());
    }

    @Test
    void pageOfTwentySixHasANextPageAndShowsTwentyFive() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(1), eq(25))).thenReturn(documents(26));

        // when
        WarehouseDocumentListPage page = service.page("s1", false, query(), PL);

        // then
        assertThat(page.rows()).hasSize(25);
        assertThat(page.pagination().nextHref()).isEqualTo("/dashboard/warehouse-documents?page=2");
        assertThat(page.pagination().openEnded()).isTrue();
        assertThat(page.fragmentPath()).isEqualTo("/dashboard/warehouse-documents/list");
    }

    @Test
    void firstOfSeveralPagesShowsTheCountedTotal() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(1), eq(25))).thenReturn(documents(26));
        when(search.count(any(), isNull())).thenReturn(OptionalInt.of(45));

        // when
        WarehouseDocumentListPage page = service.page("s1", false, query(), PL);

        // then
        assertThat(page.pagination().openEnded()).isFalse();
        assertThat(page.pagination().totalItems()).isEqualTo(45);
        assertThat(page.resultsLine()).isEqualTo("Dokumenty: 45");
    }

    @Test
    void lastPageKnowsTheTotalWithoutCounting() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(2), eq(25))).thenReturn(documents(20));
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, null, 2);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then
        assertThat(page.pagination().totalItems()).isEqualTo(45);
        assertThat(page.pagination().fromIndex()).isEqualTo(25);
        verify(search, never()).count(any(), any());
    }

    @Test
    void productCodeSearchWithANextPageIsNotCounted() {
        // given
        givenStore(true);
        when(search.search(any(), eq("5901234123457"), eq(1), eq(25))).thenReturn(documents(26));
        when(search.count(any(), eq("5901234123457"))).thenReturn(OptionalInt.empty());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, "5901234123457", 1);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then
        assertThat(page.pagination().openEnded()).isTrue();
        assertThat(page.resultsLine()).isEqualTo("Dokumenty: ponad 25");
    }

    @Test
    void countTakenBeforeANewDocumentNeverHidesTheNextPage() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(1), eq(25))).thenReturn(documents(26));
        when(search.count(any(), isNull())).thenReturn(OptionalInt.of(25));

        // when
        WarehouseDocumentListPage page = service.page("s1", false, query(), PL);

        // then
        assertThat(page.pagination().nextHref()).isEqualTo("/dashboard/warehouse-documents?page=2");
    }

    @Test
    void pagePastTheEndShowsTheEmptyStateWithoutPaging() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(9), eq(25))).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, null, 9);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then
        assertThat(page.rows()).isEmpty();
        assertThat(page.emptyState()).isNotNull();
        assertThat(page.pagination().isNeeded()).isFalse();
        assertThat(page.pagination().previousHref()).isNull();
        assertThat(page.resultsLine()).doesNotContain("201");
    }

    @Test
    void emptyListReadsTheEmptyStateToScreenReadersNotARangeOfZeros() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(1), eq(25))).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, null, 1);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then
        assertThat(page.resultsLine()).isEqualTo(page.emptyState().text()).doesNotContain("0–0");
    }

    @Test
    void pagePastTheEndOffersTheFirstPage() {
        // given
        givenStore(true);
        when(search.search(any(), any(), eq(9), eq(25))).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, DocumentKind.PZ, List.of(), null, null, null, 9);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then
        assertThat(page.emptyState().text()).isEqualTo("Brak dokumentów spełniających te warunki.");
        assertThat(page.emptyState().actionLabel()).isEqualTo("Wróć do pierwszej strony");
        assertThat(page.emptyState().actionHref()).isEqualTo(PATH + "?type=PZ");
    }

    @Test
    void criteriaCarryKindReasonsDatesAndNumberAndTheProductCodeGoesAside() {
        // given
        givenStore(true);
        ArgumentCaptor<WarehouseDocumentCriteria> criteria = ArgumentCaptor.forClass(WarehouseDocumentCriteria.class);
        when(search.search(criteria.capture(), any(), eq(1), eq(25))).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, DocumentKind.RW, List.of(DocumentReason.Theft),
                LocalDate.of(2026, 10, 1), null, "rw/mag1", 1);

        // when
        service.page("s1", false, q, PL);

        // then
        assertThat(criteria.getValue().type()).isEqualTo(DocumentType.InternalIssue);
        assertThat(criteria.getValue().reasons()).containsExactly(DocumentReason.Theft);
        assertThat(criteria.getValue().from()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(criteria.getValue().to()).isNull();
        assertThat(criteria.getValue().numberFragments()).containsExactly("rw/mag1", "RW/MAG1");
        verify(search).search(any(), isNull(), eq(1), eq(25));
    }

    @Test
    void segmentsOfferAllAndFourKindsWithFullNamesAsTitles() {
        // given
        givenStore(true);
        when(search.search(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        // when
        WarehouseDocumentListPage page = service.page("s1", false, query().withKind(DocumentKind.WZ), PL);

        // then
        assertThat(page.segments()).extracting(SegmentLink::label).containsExactly("Wszystkie", "PZ", "WZ", "PW", "RW");
        assertThat(page.segments()).filteredOn(SegmentLink::active).extracting(SegmentLink::title).containsExactly("Wydanie zewnętrzne");
        assertThat(page.reasonOptions()).extracting(Option::label).containsExactly("Wydanie do klienta", "Wysłanie do serwisu");
    }

    @Test
    void chipsNameSearchAndDatesInThatOrder() {
        // given
        givenStore(true);
        when(search.search(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, null, List.of(), LocalDate.of(2026, 10, 1), null, "5901234567890", 1);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then - with more than the search narrowing, the empty text is the generic one
        assertThat(page.chips()).extracting(Chip::label).containsExactly("Szukasz: „5901234567890”", "Data: od 01.10.2026");
        assertThat(page.dates().value()).isEqualTo("od 01.10.2026");
        assertThat(page.emptyState().text()).isEqualTo("Brak dokumentów spełniających te warunki.");
    }

    @Test
    void emptySearchQuotesWhatWasSearchedAndClearsBackToTheKind() {
        // given
        givenStore(true);
        when(search.search(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, DocumentKind.RW, List.of(), null, null, "5901234567890", 1);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, q, PL);

        // then
        assertThat(page.emptyState().text()).isEqualTo("Brak dokumentów z „5901234567890”.");
        assertThat(page.emptyState().actionHref()).isEqualTo(PATH + "?type=RW");
    }

    @Test
    void unfilteredEmptyListExplainsWhereDocumentsComeFrom() {
        // given
        givenStore(true);
        when(search.search(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        // when
        WarehouseDocumentListPage page = service.page("s1", false, query(), PL);

        // then
        assertThat(page.emptyState().text()).startsWith("Nie ma jeszcze dokumentów");
        assertThat(page.emptyState().actionHref()).isNull();
    }

    @Test
    void switchedOffStoreGetsNoQueryAndASettingsLink() {
        // given
        givenStore(false);

        // when
        WarehouseDocumentListPage page = service.page("s1", false, query(), PL);

        // then
        assertThat(page.documentsEnabled()).isFalse();
        assertThat(page.settingsHref()).isEqualTo("/dashboard/store/warehouse");
        verifyNoInteractions(search);
    }

    private static WarehouseDocumentListQuery query() {
        return new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, null, 1);
    }

    private static List<WarehouseDocument> documents(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> {
            WarehouseDocument d = new WarehouseDocument();
            d.setStoreId("s1");
            d.setDocumentId("doc-" + i);
            d.setDocumentNo("PZ/MAG1/2026/" + i);
            d.setType(DocumentType.GoodsReceipt);
            d.setCreatedAt(LocalDateTime.of(2026, 10, 7, 9, 0));
            return d;
        }).toList();
    }

    @Test
    void numberSearchRestoresTheStoredCasingOfTheStoreWarehouseId() {
        // given
        Store store = givenStore(true);
        WarehouseConfiguration warehouse = new WarehouseConfiguration();
        warehouse.setWarehouseId("MAG-uma2dqukxr");
        when(store.getWarehouseConfiguration()).thenReturn(warehouse);
        ArgumentCaptor<WarehouseDocumentCriteria> criteria = ArgumentCaptor.forClass(WarehouseDocumentCriteria.class);
        when(search.search(criteria.capture(), any(), eq(1), eq(25))).thenReturn(List.of());
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, null, List.of(), null, null,
                "pz/mag-uma2dqukxr/2026/000214", 1);

        // when
        service.page("s1", false, q, PL);

        // then
        assertThat(criteria.getValue().numberFragments()).containsExactly("pz/mag-uma2dqukxr/2026/000214",
                "PZ/MAG-UMA2DQUKXR/2026/000214", "PZ/MAG-uma2dqukxr/2026/000214");
    }

    private Store givenStore(boolean documentsEnabled) {
        Store store = mock(Store.class);
        when(store.hasDocumentsGenerationEnabled()).thenReturn(documentsEnabled);
        when(stores.findById("s1")).thenReturn(store);
        return store;
    }
}
