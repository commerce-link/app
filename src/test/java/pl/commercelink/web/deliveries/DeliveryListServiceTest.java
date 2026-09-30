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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        assertThat(page.stateReceivedOptions()).isEmpty();
        assertThat(page.stateTransitOptions()).filteredOn(o -> o.value().equals("inTransit")).extracting(DeliveriesPageModel.Option::count).containsExactly(1L);
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

    private Delivery receivedBy(String id, String provider, boolean invoiced, boolean synced) {
        Delivery d = receivedOn("store-1", id, TODAY.minusDays(3), invoiced);
        d.setProvider(provider);
        d.setSynced(synced);
        history.add(d);
        return d;
    }

    @Test
    void settleFilterNarrowsToWhatSettlementStillLacks() {
        // given
        receivedBy("aaaa0001", "Acme", false, false);
        receivedBy("aaaa0002", "Acme", true, false);
        Delivery paid = receivedBy("aaaa0003", "Acme", true, true);
        paid.setPaid(true);

        // when / then
        assertThat(page("scope", "received", "settle", "noInvoice").rows()).extracting(DeliveryRow::number).containsExactly("aaaa0001");
        assertThat(page("scope", "received", "settle", "noSync").rows()).extracting(DeliveryRow::number).containsExactly("aaaa0002");
        assertThat(page("scope", "received", "settle", "unpaid").rows()).extracting(DeliveryRow::number)
                .containsExactlyInAnyOrder("aaaa0001", "aaaa0002");
    }

    @Test
    void menuCountsEqualTheRowsTheListShowsAfterPickingTheOption() {
        // given
        receivedBy("aaaa0001", "Acme", false, false);
        receivedBy("aaaa0002", "Acme", true, false);
        receivedBy("aaaa0003", "Other", false, false);

        // when
        DeliveriesPageModel withSupplier = page("scope", "received", "provider", "Acme");

        // then
        assertThat(withSupplier.settleOptions()).filteredOn(o -> o.value().equals("noInvoice")).extracting(DeliveriesPageModel.Option::count).containsExactly(1L);
        assertThat(page("scope", "received", "provider", "Acme", "settle", "noInvoice").rows()).hasSize(1);
        assertThat(withSupplier.providerOptions()).filteredOn(o -> o.value().equals("Other")).extracting(DeliveriesPageModel.Option::count).containsExactly(1L);
        assertThat(withSupplier.providerOptions()).filteredOn(o -> o.value().equals("Acme")).extracting(DeliveriesPageModel.Option::count).containsExactly(2L);
        assertThat(page("scope", "received", "provider", "Acme", "provider", "Other").rows()).hasSize(3);
    }

    @Test
    void settleCountsFollowTheSearchToo() {
        // given
        receivedBy("aaaa0001", "Acme", false, false).setExternalDeliveryId("EXT-1");
        receivedBy("aaaa0002", "Acme", false, false).setExternalDeliveryId("OTHER-2");

        // when
        DeliveriesPageModel page = page("scope", "received", "q", "ext-1");

        // then
        assertThat(page.settleOptions()).filteredOn(o -> o.value().equals("noInvoice")).extracting(DeliveriesPageModel.Option::count).containsExactly(1L);
        assertThat(page.rows()).hasSize(1);
    }

    @Test
    void textSearchMatchesExternalNumberAndCounterpartyIgnoringCase() {
        // given
        Delivery external = onItsWay("store-1", "aaaa0001", TODAY);
        external.setExternalDeliveryId("EXT-ABC-77");
        Delivery counterparty = onItsWay("store-1", "aaaa0002", TODAY);
        counterparty.setCounterpartyShortcut("Kowalski");
        transit.add(external);
        transit.add(counterparty);

        // when / then
        assertThat(page("q", "ext-abc").rows()).extracting(DeliveryRow::number).containsExactly("aaaa0001");
        assertThat(page("q", "KOWAL").rows()).extracting(DeliveryRow::number).containsExactly("aaaa0002");
    }

    @Test
    void orderedDatesFilterTransitInclusively() {
        // given
        String[] ids = {"aaaa0001", "aaaa0002", "aaaa0003", "aaaa0004"};
        LocalDateTime[] ordered = {LocalDateTime.of(2026, 9, 20, 23, 59), LocalDateTime.of(2026, 9, 21, 0, 0),
                LocalDateTime.of(2026, 9, 22, 23, 59), LocalDateTime.of(2026, 9, 23, 0, 0)};
        for (int i = 0; i < ids.length; i++) {
            Delivery d = onItsWay("store-1", ids[i], TODAY);
            d.setOrderedAt(ordered[i]);
            transit.add(d);
        }

        // when
        DeliveriesPageModel page = page("from", "2026-09-21", "to", "2026-09-22");

        // then
        assertThat(page.rows()).extracting(DeliveryRow::number).containsExactlyInAnyOrder("aaaa0002", "aaaa0003");
    }

    @Test
    void chipsNameEveryFilterAndCarryTheirClearLinks() {
        // when
        DeliveriesPageModel page = page("provider", "Acme", "settle", "noInvoice", "from", "2026-09-21", "to", "2026-09-22", "q", "abc");

        // then
        assertThat(page.chips()).extracting(DeliveriesPageModel.Chip::label).containsExactly("Dostawca: Acme",
                "Rozliczenie: Bez faktury", "Zam\u00f3wiona: 2026-09-21 \u2013 2026-09-22", "Szukasz: \u201eabc\u201d");
        assertThat(page.chips()).extracting(DeliveriesPageModel.Chip::clearHref).containsExactly(
                "/dashboard/deliveries?settle=noInvoice&from=2026-09-21&to=2026-09-22&q=abc",
                "/dashboard/deliveries?provider=Acme&from=2026-09-21&to=2026-09-22&q=abc",
                "/dashboard/deliveries?provider=Acme&settle=noInvoice&q=abc",
                "/dashboard/deliveries?provider=Acme&settle=noInvoice&from=2026-09-21&to=2026-09-22");
        assertThat(page.chips().get(0).clearLabel()).isEqualTo("Wyczy\u015b\u0107: Dostawca: Acme");
        assertThat(page.activeFilterCount()).isEqualTo(4);
    }

    @Test
    void providerChipReadsLikeTheMenu() {
        // given
        when(labels.has("store-1", "Hurtownia Kowalski")).thenReturn(false);

        // when
        DeliveriesPageModel page = page("provider", "Hurtownia Kowalski", "provider", "Warehouse");

        // then
        assertThat(page.chips()).extracting(DeliveriesPageModel.Chip::label)
                .containsExactly("Dostawca: Inny: Hurtownia Kowalski", "Dostawca: Magazyn");
    }

    @Test
    void emptyStatesOfferTheWayOut() {
        // when
        DeliveriesPageModel filtered = page("provider", "Acme");
        DeliveriesPageModel history = page("scope", "received");
        DeliveriesPageModel fromTheStart = page("scope", "received", "period", "all");
        DeliveriesPageModel search = page("q", "zzz");

        // then
        assertThat(filtered.emptyState().text()).isEqualTo("Brak dostaw spe\u0142niaj\u0105cych filtry.");
        assertThat(filtered.emptyState().actionLabel()).isEqualTo("Wyczy\u015b\u0107 filtry");
        assertThat(filtered.emptyState().actionHref()).isEqualTo("/dashboard/deliveries");
        assertThat(history.emptyState().actionLabel()).isEqualTo("Od pocz\u0105tku");
        assertThat(history.emptyState().actionHref()).isEqualTo("/dashboard/deliveries?scope=received&period=all");
        assertThat(fromTheStart.emptyState().actionLabel()).isNull();
        assertThat(search.emptyState().actionLabel()).isEqualTo("Szukaj we wszystkich");
        assertThat(search.emptyState().actionHref()).isEqualTo("/dashboard/deliveries?scope=all&q=zzz");
    }

    @Test
    void numberSearchListsAPrefixHitInsideTheWindowOnce() {
        // given
        Delivery recent = receivedOn("store-1", "bbbb0001", TODAY.minusDays(1), true);
        history.add(recent);
        when(repository.findByDeliveryIdPrefix("store-1", "bbbb0001")).thenReturn(List.of(recent));

        // when
        DeliveriesPageModel page = page("scope", "all", "q", "bbbb0001");

        // then
        assertThat(page.rows()).extracting(DeliveryRow::number).containsExactly("bbbb0001");
    }

    @Test
    void superAdminNeverReadsTheSettlementBacklog() {
        // given
        Store one = new Store(); one.setStoreId("store-1");
        when(stores.findAll()).thenReturn(List.of(one));

        // when
        page(new DeliveryListService.ListActor(null, true, false));

        // then
        verify(repository, never()).findToSettle(any());
    }
}
