package pl.commercelink.web.deliveries.pending;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static pl.commercelink.web.deliveries.pending.PendingDeliveryRowMapperTest.*;

@ExtendWith(MockitoExtension.class)
class PendingDeliveriesServiceTest {

    private static final String PATH = "/dashboard/deliveries/preview";
    private static final Locale PL = new Locale("pl");

    @Mock
    private DeliveriesPlanningService planningService;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Store store;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private SupplierLabelMap labels;
    private PendingDeliveriesService service;

    // Acme warehouse (overdue order A), Kosatec restock (no date), dropship C at AcmeB (today, AcmeB is a GLOBAL supplier),
    // dropship D at Acme (tomorrow)
    private final Order a = order(ORDER_A, "Jan Nowak", "jan@example.com", TODAY.minusDays(2), false);
    private final Order c = order(ORDER_C, "Barbara Zając", "barbara@example.com", TODAY, true);
    private final Order d = order("da13c41a-4444-4444-8444-444444444444", "Michał Zieliński", "michal@example.com", TODAY.plusDays(1), true);

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new PendingDeliveriesService(planningService, storesRepository, supplierLabels, messages);
        lenient().when(storesRepository.findById("store-1")).thenReturn(store);
        lenient().when(store.isGlobalSupplier(anyString())).thenAnswer(invocation -> "AcmeB".equals(invocation.getArgument(0)));
        lenient().when(supplierLabels.forStore(store)).thenReturn(labels);
        lenient().when(labels.of(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    private void planning(boolean withWarehouse, boolean withDropship) {
        List<Delivery> deliveries = !withWarehouse ? List.of() : List.of(
                warehouseDelivery("Kosatec", List.of(fromWarehouse("w-1", "be quiet! Pure Power", "BN343", 4, 402.5, "Kosatec"))),
                warehouseDelivery("Acme", List.of(fromOrder(a, "1", "AMD ClearEdge Pro X3D", "MFN-CLEAR-01", 1, 1568.0, "Acme"))));
        List<DropshipCandidate> candidates = !withDropship ? List.of() : List.of(
                candidate(d, "Acme", List.of(fromOrder(d, "4", "AMD ClearEdge Pro X3D", "MFN-CLEAR-01", 1, 1568.0, "Acme"))),
                candidate(c, "AcmeB", List.of(fromOrder(c, "3", "Samsung MirageDrive 2TB NVMe", "MFN-MIRAGE-01", 1, 635.0, "AcmeB"))));
        when(planningService.plan("store-1")).thenReturn(new DeliveriesPlanningService.Planning(deliveries, candidates,
                Map.of(ORDER_A, a, ORDER_C, c, d.getOrderId(), d)));
    }

    private PendingDeliveriesPageModel page(PendingDeliveriesQuery query) {
        return service.page("store-1", false, query, TODAY, PL);
    }

    private static PendingDeliveriesQuery query(Kind kind, List<String> providers, String q) {
        return new PendingDeliveriesQuery(kind, providers, q);
    }

    @Test
    void tabsCountWhatTheFiltersLeaveAndChipsExistOnlyForSupplierAndSearch() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(Kind.WAREHOUSE, List.of("Acme"), "clearedge"));

        // then
        assertThat(page.tabs()).extracting(PendingDeliveriesPageModel.KindTab::count).containsExactly(1L, 1L);
        assertThat(page.activeKind()).isEqualTo(Kind.WAREHOUSE);
        assertThat(page.rows()).extracting(PendingDeliveryRow::key).containsExactly("Acme");
        assertThat(page.chips()).extracting(PendingDeliveriesPageModel.Chip::label)
                .containsExactly("Dostawca: Acme", "Szukaj: clearedge");
        assertThat(page.chips().get(0).clearHref()).isEqualTo(PATH + "?kind=warehouse&q=clearedge");
        assertThat(page.chips().get(1).clearHref()).isEqualTo(PATH + "?kind=warehouse&provider=Acme");
        assertThat(page.resultsLine()).isEqualTo("Wyniki: 1");
        assertThat(page.activeFilterCount()).isEqualTo(2);
    }

    @Test
    void rowsOfGlobalSuppliersAreMarkedForApproval() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel dropship = page(query(Kind.DROPSHIP, List.of(), null));

        // then
        assertThat(dropship.rows()).extracting(PendingDeliveryRow::approval).containsExactly(true, false);
        assertThat(dropship.chips()).isEmpty();
    }

    @Test
    void anUnfilteredPageHasNoChipsAndDefaultsToTheWarehouseTab() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, List.of(), null));

        // then
        assertThat(page.activeKind()).isEqualTo(Kind.WAREHOUSE);
        assertThat(page.chips()).isEmpty();
        assertThat(page.activeFilterCount()).isZero();
    }

    @Test
    void theDefaultTabMovesToDropshipWhenTheSearchLeavesNothingInTheWarehouse() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, List.of(), "barbara"));

        // then
        assertThat(page.activeKind()).isEqualTo(Kind.DROPSHIP);
        assertThat(page.dropship()).isTrue();
        assertThat(page.rows()).extracting(PendingDeliveryRow::key).containsExactly("1de57483");
        assertThat(page.tabs().get(1).active()).isTrue();
        assertThat(page.tabs().get(0).href()).isEqualTo(PATH + "?kind=warehouse&q=barbara");
    }

    @Test
    void anExplicitKindStaysEvenWhenItsTabIsEmpty() {
        // given
        planning(true, false);

        // when
        PendingDeliveriesPageModel page = page(query(Kind.DROPSHIP, List.of(), null));

        // then
        assertThat(page.activeKind()).isEqualTo(Kind.DROPSHIP);
        assertThat(page.rows()).isEmpty();
        assertThat(page.nothingPending()).isFalse();
        assertThat(page.emptyState()).isEqualTo(new PendingDeliveriesPageModel.EmptyState(
                "Żadne zamówienie nie czeka na dropshipping.", null, null, true));
    }

    @Test
    void rowsAreSortedByShippingDateWithUndatedLast() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel warehouse = page(query(Kind.WAREHOUSE, List.of(), null));
        PendingDeliveriesPageModel dropship = page(query(Kind.DROPSHIP, List.of(), null));

        // then
        assertThat(warehouse.rows()).extracting(PendingDeliveryRow::key).containsExactly("Acme", "Kosatec");
        assertThat(dropship.rows()).extracting(PendingDeliveryRow::key).containsExactly("1de57483", "da13c41a");
    }

    @Test
    void supplierAndSearchFilterBothTabs() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel bySupplier = page(query(null, List.of("Acme"), null));
        PendingDeliveriesPageModel byProduct = page(query(null, List.of(), "clearedge"));
        PendingDeliveriesPageModel byCustomer = page(query(null, List.of(), "ZAJĄC"));

        // then
        assertThat(bySupplier.tabs()).extracting(PendingDeliveriesPageModel.KindTab::count).containsExactly(1L, 1L);
        assertThat(byProduct.tabs()).extracting(PendingDeliveriesPageModel.KindTab::count).containsExactly(1L, 1L);
        assertThat(byCustomer.tabs()).extracting(PendingDeliveriesPageModel.KindTab::count).containsExactly(0L, 1L);
        assertThat(byCustomer.activeKind()).isEqualTo(Kind.DROPSHIP);
        assertThat(bySupplier.chips()).extracting(PendingDeliveriesPageModel.Chip::label).containsExactly("Dostawca: Acme");
        assertThat(bySupplier.providerSummary()).isEqualTo("Acme");
        assertThat(bySupplier.providerOptions()).extracting(PendingDeliveriesPageModel.Option::value)
                .containsExactly("Acme", "AcmeB", "Kosatec");
        assertThat(bySupplier.providerOptions().getFirst().selected()).isTrue();
        assertThat(bySupplier.providerOptions().getFirst().count()).isEqualTo(2);
    }

    @Test
    void emptyStatesTellNothingPendingFromNothingFound() {
        // given
        when(planningService.plan("store-1")).thenReturn(new DeliveriesPlanningService.Planning(List.of(), List.of(), Map.of()));

        // when
        PendingDeliveriesPageModel nothing = page(query(null, List.of(), null));

        // then
        assertThat(nothing.nothingPending()).isTrue();
        assertThat(nothing.emptyState().actionHref()).isEqualTo("/dashboard/deliveries");
        assertThat(nothing.emptyState().inline()).isFalse();

        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel filtered = page(query(Kind.WAREHOUSE, List.of(), "nothing-matches"));

        // then
        assertThat(filtered.emptyState()).isEqualTo(new PendingDeliveriesPageModel.EmptyState(
                "Brak wyników dla tych filtrów.", "Wyczyść filtry", PATH + "?kind=warehouse", false));
    }

    @Test
    void theStoreIsReadOnceForLabelsAndApproval() {
        // given
        planning(true, true);

        // when
        page(query(null, List.of(), null));

        // then
        verify(storesRepository, times(1)).findById("store-1");
        verify(supplierLabels, times(1)).forStore(store);
        verify(supplierLabels, never()).forStoreId(anyString());
    }

    @Test
    void anUnknownStoreMarksNothingForApproval() {
        // given
        planning(true, true);
        when(storesRepository.findById("store-1")).thenReturn(null);
        when(supplierLabels.forStore(null)).thenReturn(labels);

        // when
        PendingDeliveriesPageModel page = page(query(Kind.DROPSHIP, List.of(), null));

        // then
        assertThat(page.rows()).isNotEmpty().noneMatch(PendingDeliveryRow::approval);
    }

    @Test
    void removingAFilterKeepsTheTabThatWasChosenByDefault() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, List.of("AcmeB"), "zając"));

        // then
        assertThat(page.activeKind()).isEqualTo(Kind.DROPSHIP);
        assertThat(page.chips()).extracting(PendingDeliveriesPageModel.Chip::clearHref).containsExactly(
                PATH + "?kind=dropship&q=zaj%C4%85c",
                PATH + "?kind=dropship&provider=AcmeB");
        assertThat(page.clearHref()).isEqualTo(PATH + "?kind=dropship");
        assertThat(page.searchClearHref()).isEqualTo(PATH + "?kind=dropship&provider=AcmeB");
        assertThat(page.providerOptions().stream().filter(o -> o.value().equals("Acme")).findFirst().orElseThrow().toggleHref())
                .isEqualTo(PATH + "?provider=AcmeB&provider=Acme&q=zaj%C4%85c");
    }

    @Test
    void aSupplierMissingFromThePlanningIsIgnored() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel unknown = page(query(null, List.of("Gone"), null));
        PendingDeliveriesPageModel none = page(query(null, List.of(), null));
        PendingDeliveriesPageModel mixed = page(query(null, List.of("Gone", "Acme"), null));

        // then
        assertThat(unknown.rows()).isEqualTo(none.rows());
        assertThat(unknown.tabs()).isEqualTo(none.tabs());
        assertThat(unknown.chips()).isEmpty();
        assertThat(unknown.activeFilterCount()).isZero();
        assertThat(unknown.providerSummary()).isEqualTo(none.providerSummary());
        assertThat(unknown.emptyState()).isNull();
        assertThat(mixed.chips()).extracting(PendingDeliveriesPageModel.Chip::label).containsExactly("Dostawca: Acme");
        assertThat(mixed.activeFilterCount()).isEqualTo(1);
    }

    @Test
    void superAdminPathsAreStoreScoped() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = service.page("store-1", true, query(null, List.of(), null), TODAY, PL);

        // then
        assertThat(page.listPath()).isEqualTo("/dashboard/store/store-1/deliveries/preview");
        assertThat(page.fragmentPath()).isEqualTo("/dashboard/store/store-1/deliveries/preview/fragment");
        assertThat(page.clearHref()).isEqualTo("/dashboard/store/store-1/deliveries/preview?kind=warehouse");
        assertThat(page.rows().getFirst().createHref()).isEqualTo("/dashboard/store/store-1/deliveries/create/Acme");
    }
}
