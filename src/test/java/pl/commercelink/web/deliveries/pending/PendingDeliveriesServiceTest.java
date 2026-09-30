package pl.commercelink.web.deliveries.pending;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.deliveries.SupplierOrderingModes.OrderingMode;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Focus;
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
    private SupplierOrderingModes orderingModes;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private SupplierLabelMap labels;
    private PendingDeliveriesService service;

    // Acme warehouse (overdue order A), Kosatec restock (no date), dropship C at AcmeB (today), dropship D at Acme (in 6 days)
    private final Order a = order(ORDER_A, "Jan Nowak", "jan@example.com", TODAY.minusDays(2), false);
    private final Order c = order(ORDER_C, "Barbara Zając", "barbara@example.com", TODAY, true);
    private final Order d = order("da13c41a-4444-4444-8444-444444444444", "Michał Zieliński", "michal@example.com", TODAY.plusDays(6), true);

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new PendingDeliveriesService(planningService, orderingModes, supplierLabels, messages);
        lenient().when(supplierLabels.forStoreId(anyString())).thenReturn(labels);
        lenient().when(labels.of(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(orderingModes.of(anyString(), anyCollection())).thenReturn(Map.of("AcmeB", new OrderingMode(true, true)));
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

    private static PendingDeliveriesQuery query(Kind kind, Focus focus, List<String> providers, String q) {
        return new PendingDeliveriesQuery(kind, focus, providers, q);
    }

    @Test
    void tilesCountEverythingAndTabsCountWhatTheFiltersLeave() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, Focus.OVERDUE, List.of(), null));

        // then
        assertThat(page.tiles()).extracting(PendingDeliveriesPageModel.Tile::value)
                .containsExactly("1", "1", "1", "5 381,00 PLN");
        assertThat(page.tiles().get(0).active()).isTrue();
        assertThat(page.tiles().get(0).href()).isEqualTo(PATH + "?kind=warehouse");
        assertThat(page.tiles().get(1).href()).isEqualTo(PATH + "?focus=today");
        assertThat(page.tiles().get(2).href()).isNull();
        assertThat(page.tabs()).extracting(PendingDeliveriesPageModel.KindTab::count).containsExactly(1L, 0L);
        assertThat(page.activeKind()).isEqualTo(Kind.WAREHOUSE);
        assertThat(page.rows()).extracting(PendingDeliveryRow::key).containsExactly("Acme");
        assertThat(page.chips()).extracting(PendingDeliveriesPageModel.Chip::label).containsExactly("Po terminie");
        assertThat(page.resultsLine()).isEqualTo("Wyniki: 1");
    }

    @Test
    void theDefaultTabMovesToDropshipWhenTheFiltersLeaveNothingInTheWarehouse() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, Focus.TODAY, List.of(), null));

        // then
        assertThat(page.activeKind()).isEqualTo(Kind.DROPSHIP);
        assertThat(page.dropship()).isTrue();
        assertThat(page.rows()).extracting(PendingDeliveryRow::key).containsExactly("1de57483");
        assertThat(page.tabs().get(1).active()).isTrue();
        assertThat(page.tabs().get(0).href()).isEqualTo(PATH + "?kind=warehouse&focus=today");
    }

    @Test
    void anExplicitKindStaysEvenWhenItsTabIsEmpty() {
        // given
        planning(true, false);

        // when
        PendingDeliveriesPageModel page = page(query(Kind.DROPSHIP, null, List.of(), null));

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
        PendingDeliveriesPageModel warehouse = page(query(Kind.WAREHOUSE, null, List.of(), null));
        PendingDeliveriesPageModel dropship = page(query(Kind.DROPSHIP, null, List.of(), null));

        // then
        assertThat(warehouse.rows()).extracting(PendingDeliveryRow::key).containsExactly("Acme", "Kosatec");
        assertThat(dropship.rows()).extracting(PendingDeliveryRow::key).containsExactly("1de57483", "da13c41a");
    }

    @Test
    void supplierAndSearchFilterBothTabs() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel bySupplier = page(query(null, null, List.of("Acme"), null));
        PendingDeliveriesPageModel byProduct = page(query(null, null, List.of(), "clearedge"));
        PendingDeliveriesPageModel byCustomer = page(query(null, null, List.of(), "ZAJĄC"));

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
        PendingDeliveriesPageModel nothing = page(query(null, null, List.of(), null));

        // then
        assertThat(nothing.nothingPending()).isTrue();
        assertThat(nothing.emptyState().actionHref()).isEqualTo("/dashboard/deliveries");
        assertThat(nothing.emptyState().inline()).isFalse();
        assertThat(nothing.tiles()).extracting(PendingDeliveriesPageModel.Tile::value).containsExactly("0", "0", "0", "0,00 PLN");
        verifyNoInteractions(orderingModes);

        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel filtered = page(query(Kind.WAREHOUSE, null, List.of(), "nothing-matches"));

        // then
        assertThat(filtered.emptyState()).isEqualTo(new PendingDeliveriesPageModel.EmptyState(
                "Brak wyników dla tych filtrów.", "Wyczyść filtry", PATH + "?kind=warehouse", false));
    }

    @Test
    void orderingModesAreAskedOnceForEverySupplierOfThePage() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, null, List.of(), null));

        // then
        verify(orderingModes, times(1)).of(eq("store-1"), argThat(p -> new HashSet<>(p).equals(Set.of("Acme", "Kosatec", "AcmeB"))));
        assertThat(page.tiles().get(2).value()).isEqualTo("1");
    }

    @Test
    void removingAFilterKeepsTheTabThatWasChosenByDefault() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel page = page(query(null, Focus.TODAY, List.of("AcmeB"), "zając"));

        // then
        assertThat(page.activeKind()).isEqualTo(Kind.DROPSHIP);
        assertThat(page.chips()).extracting(PendingDeliveriesPageModel.Chip::clearHref).containsExactly(
                PATH + "?kind=dropship&provider=AcmeB&q=zaj%C4%85c",
                PATH + "?kind=dropship&focus=today&q=zaj%C4%85c",
                PATH + "?kind=dropship&focus=today&provider=AcmeB");
        assertThat(page.clearHref()).isEqualTo(PATH + "?kind=dropship");
        assertThat(page.searchClearHref()).isEqualTo(PATH + "?kind=dropship&focus=today&provider=AcmeB");
        assertThat(page.tiles().get(1).active()).isTrue();
        assertThat(page.tiles().get(1).href()).isEqualTo(PATH + "?kind=dropship&provider=AcmeB&q=zaj%C4%85c");
        assertThat(page.tiles().get(0).href()).isEqualTo(PATH + "?focus=overdue&provider=AcmeB&q=zaj%C4%85c");
        assertThat(page.providerOptions().stream().filter(o -> o.value().equals("Acme")).findFirst().orElseThrow().toggleHref())
                .isEqualTo(PATH + "?focus=today&provider=AcmeB&provider=Acme&q=zaj%C4%85c");
    }

    @Test
    void aSupplierMissingFromThePlanningIsIgnored() {
        // given
        planning(true, true);

        // when
        PendingDeliveriesPageModel unknown = page(query(null, null, List.of("Gone"), null));
        PendingDeliveriesPageModel none = page(query(null, null, List.of(), null));
        PendingDeliveriesPageModel mixed = page(query(null, null, List.of("Gone", "Acme"), null));

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
        PendingDeliveriesPageModel page = service.page("store-1", true, query(null, Focus.OVERDUE, List.of(), null), TODAY, PL);

        // then
        assertThat(page.listPath()).isEqualTo("/dashboard/store/store-1/deliveries/preview");
        assertThat(page.fragmentPath()).isEqualTo("/dashboard/store/store-1/deliveries/preview/fragment");
        assertThat(page.clearHref()).isEqualTo("/dashboard/store/store-1/deliveries/preview?kind=warehouse");
        assertThat(page.rows().getFirst().createHref()).isEqualTo("/dashboard/store/store-1/deliveries/create/Acme");
    }
}
