package pl.commercelink.web.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryListServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final Locale PL = new Locale("pl");

    @Mock DeliveriesRepository repository;
    @Mock SupplierLabels supplierLabels;
    @Mock SupplierLabelMap labels;
    @Mock StoresRepository stores;
    DeliveryListService service;

    final List<Delivery> transit = new ArrayList<>();
    final List<Delivery> toSettle = new ArrayList<>();
    final List<Delivery> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        service = new DeliveryListService(repository, supplierLabels, stores, messages);
        when(repository.findInTransit(anyString())).thenAnswer(inv -> transit.stream().filter(d -> d.getStoreId().equals(inv.getArgument(0))).toList());
        when(repository.findToSettle(anyString())).thenAnswer(inv -> toSettle.stream().filter(d -> d.getStoreId().equals(inv.getArgument(0))).toList());
        when(repository.findReceivedBetween(anyString(), any(), any())).thenAnswer(inv -> history.stream()
                .filter(d -> d.getStoreId().equals(inv.getArgument(0)))
                .filter(d -> inv.getArgument(1) == null || !d.getReceivedAt().toLocalDate().isBefore(inv.getArgument(1)))
                .toList());
        when(repository.findByDeliveryIdPrefix(anyString(), anyString())).thenReturn(List.of());
        when(supplierLabels.forStoreId(anyString())).thenReturn(labels);
        when(supplierLabels.forStoreIds(any())).thenReturn(labels);
        when(labels.of(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(labels.has(anyString())).thenReturn(true);
        when(labels.has(anyString(), anyString())).thenReturn(true);
        when(labels.options()).thenReturn(List.of(new SupplierLabelMap.Option("Acme", "Acme")));
    }

    static Delivery onItsWay(String storeId, String id, LocalDate planned) {
        Delivery d = new Delivery();
        d.setStoreId(storeId);
        d.setDeliveryId(id + "-0000-0000-0000-000000000000");
        d.setProvider("Acme");
        d.setType(DeliveryType.WAREHOUSE);
        d.setEstimatedDeliveryAt(planned);
        d.setOrderedAt(LocalDateTime.of(2026, 9, 20, 8, 0));
        d.setConnectionMode(ConnectionMode.OWN);
        return d;
    }

    static Delivery receivedOn(String storeId, String id, LocalDate day, boolean invoiced) {
        Delivery d = onItsWay(storeId, id, day);
        d.setReceivedAt(day.atTime(10, 0));
        d.setInvoiced(invoiced);
        return d;
    }

    private DeliveriesPageModel page(String... pairs) {
        return page(new DeliveryListService.ListActor("store-1", false, true), pairs);
    }

    private DeliveriesPageModel page(DeliveryListService.ListActor actor, String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.add(pairs[i], pairs[i + 1]);
        return service.page(actor, DeliveryListQuery.parse(params), TODAY, PL);
    }

    @Test
    void tilesCountWhatTheListShowsWhenClicked() {
        // given
        transit.add(onItsWay("store-1", "aaaa0001", LocalDate.of(2026, 9, 27)));
        transit.add(onItsWay("store-1", "aaaa0002", TODAY));
        Delivery failed = onItsWay("store-1", "aaaa0003", LocalDate.of(2026, 10, 6));
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);
        transit.add(failed);
        toSettle.add(receivedOn("store-1", "bbbb0001", LocalDate.of(2026, 3, 1), false));

        // when
        DeliveriesPageModel page = page();

        // then
        assertThat(page.tiles()).extracting(DeliveriesPageModel.Tile::count).containsExactly(1L, 1L, 1L, 1L);
        assertThat(page("focus", "overdue").rows()).extracting(DeliveryRow::number).containsExactly("aaaa0001");
        assertThat(page("focus", "invoice").rows()).extracting(DeliveryRow::number).containsExactly("bbbb0001");
        assertThat(page("focus", "invoice").query().scope()).isEqualTo(DeliveryListQuery.Scope.RECEIVED);
    }

    @Test
    void sortingCoversEveryPageNotJustOne() {
        // given: 30 deliveries in reverse date order, more than one page
        for (int i = 0; i < 30; i++) {
            transit.add(onItsWay("store-1", String.format("aa%06d", i), TODAY.plusDays(30 - i)));
        }

        // when
        DeliveriesPageModel first = page();
        DeliveriesPageModel second = page("page", "2");

        // then
        assertThat(first.rows()).hasSize(25);
        assertThat(first.rows().get(0).number()).isEqualTo("aa000029");
        assertThat(second.rows()).extracting(DeliveryRow::number).containsExactly("aa000004", "aa000003", "aa000002", "aa000001", "aa000000");
        assertThat(first.pagination().totalItems()).isEqualTo(30);
    }

    @Test
    void undatedDeliveriesStayLastInBothDirections() {
        // given
        transit.add(onItsWay("store-1", "aaaa0001", null));
        transit.add(onItsWay("store-1", "aaaa0002", TODAY));
        transit.add(onItsWay("store-1", "aaaa0003", TODAY.plusDays(3)));

        // then
        assertThat(page().rows()).extracting(DeliveryRow::number).containsExactly("aaaa0002", "aaaa0003", "aaaa0001");
        assertThat(page("sort", "due", "dir", "desc").rows()).extracting(DeliveryRow::number)
                .containsExactly("aaaa0003", "aaaa0002", "aaaa0001");
    }

    @Test
    void allScopeListsOnTheWayFirstThenTheHistory() {
        // given
        transit.add(onItsWay("store-1", "aaaa0001", TODAY.plusDays(2)));
        history.add(receivedOn("store-1", "bbbb0001", TODAY.minusDays(1), true));
        history.add(receivedOn("store-1", "bbbb0002", TODAY.minusDays(200), true));

        // when
        DeliveriesPageModel page = page("scope", "all");

        // then: the 200-day-old one is outside the default 90-day window
        assertThat(page.rows()).extracting(DeliveryRow::number).containsExactly("aaaa0001", "bbbb0001");
        assertThat(page("scope", "all", "period", "all").rows()).hasSize(3);
    }

    @Test
    void searchByNumberFindsADeliveryOutsideTheHistoryWindow() {
        // given
        Delivery old = receivedOn("store-1", "cccc0001", TODAY.minusDays(400), true);
        when(repository.findByDeliveryIdPrefix("store-1", "cccc0001")).thenReturn(List.of(old));

        // when
        DeliveriesPageModel page = page("scope", "all", "q", "CCCC0001");

        // then
        assertThat(page.rows()).extracting(DeliveryRow::number).containsExactly("cccc0001");
    }

    @Test
    void stateMenuOffersOnlyTheStatesOfTheScopeWithCounts() {
        // given
        transit.add(onItsWay("store-1", "aaaa0001", TODAY));
        Delivery pending = onItsWay("store-1", "aaaa0002", TODAY);
        pending.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        transit.add(pending);

        // when
        DeliveriesPageModel page = page("state", "orderPending");

        // then
        assertThat(page.stateOptions()).extracting(DeliveriesPageModel.Option::value).doesNotContain("received", "shippedToCustomer");
        assertThat(page.stateOptions()).filteredOn(o -> o.value().equals("inTransit")).extracting(DeliveriesPageModel.Option::count).containsExactly(1L);
        assertThat(page.rows()).extracting(DeliveryRow::number).containsExactly("aaaa0002");
        assertThat(page.chips()).extracting(DeliveriesPageModel.Chip::label).containsExactly("Stan: W trakcie zamawiania");
    }

    @Test
    void superAdminSeesOnlyGlobalDeliveriesOfEveryStore() {
        // given
        Store one = new Store(); one.setStoreId("store-1");
        Store two = new Store(); two.setStoreId("store-2");
        when(stores.findAll()).thenReturn(List.of(one, two));
        Delivery global = onItsWay("store-2", "aaaa0001", TODAY);
        global.setConnectionMode(ConnectionMode.GLOBAL);
        global.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        transit.add(global);
        transit.add(onItsWay("store-1", "aaaa0002", TODAY));

        // when
        DeliveriesPageModel page = page(new DeliveryListService.ListActor(null, true, false));

        // then
        assertThat(page.rows()).extracting(DeliveryRow::storeId).containsExactly("store-2");
        assertThat(page.rows().get(0).href()).startsWith("/dashboard/store/store-2/deliveries/details");
        assertThat(page.tiles()).extracting(DeliveriesPageModel.Tile::label).first().isEqualTo("Do akceptacji");
    }

    @Test
    void storeWithoutConnectionsStillListsWarehouseAndTypedSuppliers() {
        // given
        when(labels.options()).thenReturn(List.of());
        when(labels.has("store-1", "Hurtownia Kowalski")).thenReturn(false);
        Delivery typed = onItsWay("store-1", "aaaa0001", TODAY);
        typed.setProvider("Hurtownia Kowalski");
        transit.add(typed);

        // when
        DeliveriesPageModel page = page();

        // then
        assertThat(page.providerOptions()).extracting(DeliveriesPageModel.Option::value)
                .containsExactlyInAnyOrder("Warehouse", "Hurtownia Kowalski");
    }

    @Test
    void emptyTransitWithoutFiltersPointsToTheReceivedDeliveries() {
        // when
        DeliveriesPageModel page = page();

        // then
        assertThat(page.emptyState().actionHref()).isEqualTo("/dashboard/deliveries?scope=received");
    }
}
