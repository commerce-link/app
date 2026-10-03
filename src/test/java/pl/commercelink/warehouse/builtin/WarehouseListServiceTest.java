package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static pl.commercelink.orders.FulfilmentStatus.*;

@ExtendWith(MockitoExtension.class)
class WarehouseListServiceTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private WarehouseRepository repository;
    @Mock
    private DeliveriesRepository deliveries;

    private WarehouseListService service;
    private final List<WarehouseItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new WarehouseListService(repository, deliveries, new DeliveryRedirectResolver(), messages);
        when(repository.findAllFiltered(eq("store-1"), isNull(), anyList())).thenAnswer(inv -> {
            List<FulfilmentStatus> statuses = inv.getArgument(2);
            return items.stream().filter(i -> statuses.contains(i.getStatus())).toList();
        });
    }

    private WarehouseItem add(String name, String category, FulfilmentStatus status, int qty, double cost) {
        WarehouseItem item = new WarehouseItem("store-1", "d-" + items.size(), category, name, "590", "MFN-" + items.size(), cost, qty);
        item.setItemId("id-" + items.size());
        item.setStatus(status);
        items.add(item);
        return item;
    }

    private WarehousePageModel page(String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.add(pairs[i], pairs[i + 1]);
        return service.page("store-1", false, true, WarehouseListQuery.parse(params, false), PL);
    }

    @Test
    void tilesCountTheWholeWarehouseWhateverTheFilter() {
        // given
        add("A", "GPU", Delivered, 3, 100);
        add("B", "CPU", Reserved, 2, 50);
        add("C", "CPU", InRMA, 1, 10);
        add("D", "CPU", InExternalService, 1, 1000);

        // when
        WarehousePageModel model = page("categories", "GPU");

        // then
        assertThat(model.tiles()).extracting(WarehousePageModel.Tile::value)
                .containsExactly("3 szt.", "0 szt.", "2 szt.", "2 szt.");
        assertThat(model.tiles().get(0).active()).isFalse();
        assertThat(page().tiles().get(0).active()).isTrue();
    }

    @Test
    void ownWarehouseTilesAreFourLinksWithToReceiveCountingAllocationAndOrdered() {
        // given
        add("A", "GPU", Delivered, 3, 100);
        add("B", "CPU", Allocation, 2, 50);
        add("C", "CPU", Ordered, 4, 10);
        add("D", "CPU", Reserved, 1, 10);

        // when
        WarehousePageModel model = page();

        // then
        assertThat(model.tiles()).extracting(WarehousePageModel.Tile::label)
                .containsExactly("Na stanie", "Do przyjęcia", "Zarezerwowane", "Wymaga uwagi");
        assertThat(model.tiles()).extracting(WarehousePageModel.Tile::href).doesNotContainNull();
        WarehousePageModel.Tile toReceive = model.tiles().get(1);
        assertThat(toReceive.value()).isEqualTo("6 szt.");
        assertThat(toReceive.hint()).isEqualTo("w alokacji i zamówione u dostawców");
        assertThat(toReceive.href()).isEqualTo("/dashboard/warehouse?statuses=Allocation&statuses=Ordered");
        assertThat(toReceive.active()).isFalse();
    }

    @Test
    void toReceiveTileIsActiveOnlyForExactlyItsStatusesWithoutOtherNarrowing() {
        // given
        add("A", "GPU", Allocation, 1, 1);
        add("B", "CPU", Ordered, 1, 1);

        // when / then
        assertThat(page("statuses", "Allocation", "statuses", "Ordered").tiles().get(1).active()).isTrue();
        assertThat(page("statuses", "Allocation").tiles().get(1).active()).isFalse();
        assertThat(page("statuses", "Allocation", "statuses", "Ordered", "categories", "GPU").tiles().get(1).active()).isFalse();
    }

    @Test
    void wmsTilesEndWithAllLinkingToEveryStatus() {
        // given
        add("A", "GPU", Ordered, 3, 1);
        add("B", "GPU", Allocation, 2, 1);
        add("C", "GPU", New, 1, 1);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        // when
        WarehousePageModel model = service.page("store-1", true, true, WarehouseListQuery.parse(params, true), PL);
        params.add("statuses", "all");
        WarehousePageModel all = service.page("store-1", true, true, WarehouseListQuery.parse(params, true), PL);

        // then
        assertThat(model.tiles()).extracting(WarehousePageModel.Tile::label)
                .containsExactly("Zamówione", "W alokacji", "Nowe", "Wszystkie");
        WarehousePageModel.Tile everything = model.tiles().get(3);
        assertThat(everything.value()).isEqualTo("6 szt.");
        assertThat(everything.hint()).isEqualTo("nowe, w alokacji i zamówione");
        assertThat(everything.href()).isEqualTo("/dashboard/warehouse?statuses=all");
        assertThat(everything.active()).isFalse();
        assertThat(all.tiles().get(3).active()).isTrue();
        assertThat(model.tiles()).extracting(WarehousePageModel.Tile::href).doesNotContainNull();
    }

    @Test
    void resultsLineShowsNetAndGrossValueOfEveryFilteredItemAcrossPages() {
        // given
        for (int i = 0; i < 55; i++) add("P" + i, "GPU", Delivered, 2, 10);
        add("Other", "CPU", Delivered, 1, 100);

        // when
        WarehousePageModel first = page("categories", "GPU");
        WarehousePageModel everything = page();

        // then
        assertThat(first.rows()).hasSize(50);
        assertThat(first.resultsLine()).isEqualTo("Pozycje: 55 · 110 szt. · Wartość netto: 1\u00a0100,00 PLN · brutto: 1\u00a0353,00 PLN");
        assertThat(everything.resultsLine()).isEqualTo("Pozycje: 56 · 111 szt. · Wartość netto: 1\u00a0200,00 PLN · brutto: 1\u00a0476,00 PLN");
    }

    @Test
    void categoriesComeOnlyFromThisStoresVisibleItemsWithCountsAndNoCategoryLast() {
        // given
        add("A", "GPU", Delivered, 1, 1);
        add("B", "CPU", Delivered, 1, 1);
        add("C", null, Delivered, 1, 1);
        add("D", "Uncategorized", Reserved, 1, 1);
        add("E", "Monitors", Destroyed, 1, 1);

        // when
        WarehousePageModel model = page();

        // then
        assertThat(model.categoryOptions()).extracting(WarehousePageModel.Option::label)
                .containsExactly("CPU", "GPU", "Bez kategorii");
        assertThat(model.categoryOptions()).extracting(WarehousePageModel.Option::count).containsExactly(1L, 1L, 1L);
        assertThat(model.categoryOptions().get(2).value()).isEqualTo(WarehouseListQuery.NO_CATEGORY);
    }

    @Test
    void categoryFromAddressWithoutItemsStaysSelectableWithZero() {
        // given
        add("A", "GPU", Delivered, 1, 1);

        // when
        WarehousePageModel model = page("categories", "Monitors");

        // then
        assertThat(model.categoryOptions()).anySatisfy(o -> {
            assertThat(o.label()).isEqualTo("Monitors");
            assertThat(o.count()).isZero();
            assertThat(o.selected()).isTrue();
        });
        assertThat(model.rows()).isEmpty();
        assertThat(model.emptyState().text()).isEqualTo("Nic nie pasuje do filtrów.");
        assertThat(model.storeEmpty()).isFalse();
    }

    @Test
    void statusCountsLeaveOutTheStatusFilterAndRespectCategory() {
        // given
        add("A", "GPU", Delivered, 1, 1);
        add("B", "GPU", Reserved, 1, 1);
        add("C", "CPU", Reserved, 1, 1);

        // when
        WarehousePageModel model = page("categories", "GPU");

        // then
        assertThat(model.statusOptions()).extracting(WarehousePageModel.Option::count)
                .containsExactly(1L, 1L, 0L, 0L, 0L, 0L, 0L);
        assertThat(model.statusSummary()).isEqualTo("Na stanie");
    }

    @Test
    void searchMatchesNameCodesDeliverySerialAndComment() {
        // given
        add("Gigabyte RTX", "GPU", Delivered, 1, 1).setSerialNo("SN-ABC");
        add("Intel i7", "CPU", Delivered, 1, 1).setComment("zwrot od klienta");

        // when / then
        assertThat(page("q", "rtx").rows()).extracting(WarehouseItemRow::name).containsExactly("Gigabyte RTX");
        assertThat(page("q", "sn-abc").rows()).hasSize(1);
        assertThat(page("q", "KLIENTA").rows()).extracting(WarehouseItemRow::name).containsExactly("Intel i7");
        assertThat(page("q", "MFN-1").rows()).hasSize(1);
    }

    @Test
    void defaultSortIsCategoryThenNameWithNoCategoryLast() {
        // given
        add("Zeta", "GPU", Delivered, 1, 1);
        add("Alfa", "GPU", Delivered, 1, 1);
        add("Beta", "CPU", Delivered, 1, 1);
        add("Usługa", null, Delivered, 1, 1);

        // when / then
        assertThat(page().rows()).extracting(WarehouseItemRow::name).containsExactly("Beta", "Alfa", "Zeta", "Usługa");
        assertThat(page("sort", "qty", "dir", "desc").sortHeaders().get(WarehouseListQuery.Sort.QTY).ariaSort()).isEqualTo("descending");
    }

    @Test
    void pagesByFiftyAndCountsUnits() {
        // given
        for (int i = 0; i < 55; i++) add("P" + i, "GPU", Delivered, 2, 1);

        // when
        WarehousePageModel first = page();

        // then
        assertThat(first.rows()).hasSize(50);
        assertThat(first.pagination().isNeeded()).isTrue();
        assertThat(first.resultsLine()).startsWith("Pozycje: 55 · 110 szt.");
        assertThat(page("page", "2").rows()).hasSize(5);
    }

    @Test
    void emptyStoreHasItsOwnEmptyStateAndChipsNameFilters() {
        // when
        WarehousePageModel empty = page();
        add("A", "GPU", Reserved, 1, 1);
        WarehousePageModel filtered = page("statuses", "Reserved", "categories", "GPU", "q", "a");

        // then
        assertThat(empty.storeEmpty()).isTrue();
        assertThat(empty.emptyState().text()).startsWith("Magazyn jest pusty");
        assertThat(empty.emptyState().actionLabel()).isEqualTo("Dodaj pozycję");
        assertThat(empty.emptyState().actionHref()).isEqualTo("/dashboard/warehouse/items/new");
        assertThat(filtered.chips()).extracting(WarehousePageModel.Chip::label)
                .containsExactly("Status: Zarezerwowane", "Kategoria: GPU", "Szukaj: a");
        assertThat(page("statuses", "all").chips()).isEmpty();
        assertThat(filtered.activeFilterCount()).isEqualTo(3);
    }

    @Test
    void bulkMenuCarriesTheMatrixAndReasons() {
        // given
        add("A", "GPU", Delivered, 1, 1);

        // when
        WarehousePageModel model = page();

        // then
        WarehousePageModel.BulkActionView reserve = model.menuActions().get(0);
        assertThat(reserve.path()).isEqualTo("/dashboard/warehouse/markAsReserved");
        assertThat(reserve.forStatuses()).isEqualTo("Delivered");
        assertThat(reserve.statusReason()).isEqualTo("Tylko dla: Na stanie");
        assertThat(model.destroyAction().danger()).isTrue();
        assertThat(model.destroyReasons()).extracting(WarehousePageModel.Option::value)
                .containsExactly("StockAdjustment", "Destruction", "InternalUse", "Theft");
    }

    @Test
    void destroyedItemsAreCountedForTheArchiveLinkOnly() {
        // given
        add("A", "GPU", Destroyed, 1, 1);
        add("B", "GPU", Delivered, 1, 1);

        // when / then
        assertThat(page().destroyedCount()).isEqualTo(1);
        assertThat(page("statuses", "all").rows()).hasSize(1);
    }
}
