package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestroyedListServiceTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Mock private WarehouseRepository repository;
    @Mock private SupplierLabels supplierLabels;
    @Mock private SupplierLabelMap labelMap;
    @Mock private SupplierRegistry suppliers;
    @Mock private DeliveryRedirectResolver redirects;

    private DestroyedListService service;
    private final List<WarehouseItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new DestroyedListService(repository, redirects, messages, supplierLabels, suppliers);
        when(supplierLabels.forStoreId("store-1")).thenReturn(labelMap);
        when(repository.findAll("store-1", FulfilmentStatus.Destroyed)).thenReturn(items);
        when(redirects.pointsToDelivery(any())).thenReturn(true);
        when(redirects.resolveFor(any())).thenReturn("/dashboard/deliveries/details?deliveryId=d");
    }

    private WarehouseItem destroyed(String name, String category, LocalDate on, double cost, int qty) {
        WarehouseItem item = new WarehouseItem("store-1", "d-" + name, category, name, "880", "MFN-" + name, cost, qty);
        item.setStatus(FulfilmentStatus.Destroyed);
        item.setDestroyedAt(on);
        items.add(item);
        return item;
    }

    private DestroyedPageModel page(String... kv) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < kv.length; i += 2) params.add(kv[i], kv[i + 1]);
        return service.page("store-1", false, DestroyedListQuery.parse(params), TODAY, PL);
    }

    @Test
    void tilesCountTheirPeriodsAndIgnoreOtherFilters() {
        // given
        destroyed("A", "SSD", LocalDate.of(2026, 10, 6), 100, 1);
        destroyed("B", "RAM", LocalDate.of(2026, 9, 15), 200, 2);
        destroyed("C", "SSD", LocalDate.of(2025, 1, 1), 1000, 1);

        // when
        DestroyedPageModel page = page("q", "A");

        // then
        assertThat(page.tiles()).extracting(WarehousePageModel.Tile::value).containsExactly("100,00", "400,00", "500,00", "1\u00a0500,00");
        assertThat(page.tiles()).extracting(WarehousePageModel.Tile::active).containsExactly(false, false, false, true);
    }

    @Test
    void newestFirstByDefaultAndItemsWithoutDateLast() {
        // given
        destroyed("Old", "SSD", LocalDate.of(2026, 1, 1), 1, 1);
        destroyed("NoDate", "SSD", null, 1, 1);
        destroyed("New", "SSD", LocalDate.of(2026, 10, 1), 1, 1);

        // when
        DestroyedPageModel page = page();

        // then
        assertThat(page.rows()).extracting(r -> r.item().name()).containsExactly("New", "Old", "NoDate");
        assertThat(page.rows().get(2).destroyedOn()).isEqualTo("\u2014");
    }

    @Test
    void itemsWithoutDateSortLastAndCountOnlyInAll() {
        // given
        destroyed("NoDate", "SSD", null, 10, 1);

        // when
        DestroyedPageModel all = page();
        DestroyedPageModel month = page("period", "month");

        // then
        assertThat(all.tiles().get(3).value()).isEqualTo("10,00");
        assertThat(all.tiles().get(0).value()).isEqualTo("0,00");
        assertThat(month.rows()).isEmpty();
        assertThat(page("sort", "date", "dir", "asc").rows()).extracting(r -> r.item().name()).containsExactly("NoDate");
    }

    @Test
    void searchFindsSerialNumbersAndComments() {
        // given
        destroyed("A", "SSD", TODAY, 1, 1).setSerialNo("S7KH123");
        destroyed("B", "SSD", TODAY, 1, 1).setComment("Zalany w magazynie B");
        destroyed("C", "SSD", TODAY, 1, 1);

        // when / then
        assertThat(page("q", "s7kh").rows()).extracting(r -> r.item().name()).containsExactly("A");
        assertThat(page("q", "zalany").rows()).extracting(r -> r.item().name()).containsExactly("B");
    }

    @Test
    void resultsLineCountsWhatTheListShows() {
        // given
        destroyed("A", "SSD", TODAY, 100, 2);
        destroyed("B", "RAM", TODAY, 50, 1);

        // when
        DestroyedPageModel page = page("categories", "SSD");

        // then
        assertThat(page.results()).isEqualTo("Ilo\u015b\u0107 produkt\u00f3w: 1, Ilo\u015b\u0107 sztuk: 2 \u00b7 Warto\u015b\u0107 netto: 200,00 PLN");
        assertThat(page.categoryOptions()).extracting(WarehousePageModel.Option::value).containsExactly("RAM", "SSD");
    }

    @Test
    void rowMenuLinksToDocumentsAndHistoryAndTheRowCarriesTheArchive() {
        // given
        destroyed("A", "SSD", TODAY, 1, 1).setSerialNo("S1, S2");

        // when
        DestroyedPageModel.Row row = page("period", "month").rows().get(0);

        // then
        assertThat(row.documentsHref()).contains("/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=d-A&mfn=MFN-A&from=item&itemId=");
        assertThat(row.historyHref()).isEqualTo("/dashboard/item/history?serialNo=S1");
        assertThat(row.item().href()).contains("?back=%2Fdashboard%2Fwarehouse%2Fitems%2Fdestroyed%3Fperiod%3Dmonth");
    }

    @Test
    void emptyStoreAndEmptyFilterSayDifferentThings() {
        // when
        DestroyedPageModel none = page();
        destroyed("A", "SSD", TODAY, 1, 1);
        DestroyedPageModel filtered = page("q", "zzz");

        // then
        assertThat(none.storeEmpty()).isTrue();
        assertThat(none.emptyState().text()).isEqualTo("Nic jeszcze nie zniszczono.");
        assertThat(filtered.storeEmpty()).isFalse();
        assertThat(filtered.emptyState().actionHref()).isEqualTo("/dashboard/warehouse/items/destroyed");
    }

    @Test
    void pagesHoldFiftyRows() {
        // given
        for (int i = 0; i < 51; i++) destroyed("I" + i, "SSD", TODAY, 1, 1);

        // when / then
        assertThat(page().rows()).hasSize(50);
        assertThat(page("page", "2").rows()).hasSize(1);
        assertThat(page().pagination().nextHref()).isEqualTo("/dashboard/warehouse/items/destroyed?page=2");
    }
}
