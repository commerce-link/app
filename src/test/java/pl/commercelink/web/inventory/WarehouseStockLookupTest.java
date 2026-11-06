package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseStockLookupTest {

    private static final String EAN_A = "5900000000011";
    private static final String EAN_B = "5900000000028";
    private static final String EAN_C = "5900000000035";

    @Mock private Warehouse warehouse;
    @Mock private StoresRepository storesRepository;
    @Mock private StockQueryService stock;
    private WarehouseStockLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new WarehouseStockLookup(warehouse, storesRepository);
        lenient().when(storesRepository.findById("store-1")).thenReturn(store("store-1"));
        lenient().when(storesRepository.findById("store-2")).thenReturn(store("store-2"));
        lenient().when(warehouse.stockQueryService("store-1")).thenReturn(stock);
        lenient().when(warehouse.stockQueryService("store-2")).thenReturn(stock);
    }

    @Test
    void countsInStockQuantityPerUnifiedEan() {
        // given
        when(stock.searchAllAvailable("store-1")).thenReturn(List.of(
                item("store-1", "0" + EAN_A, "MFN-A", 4, FulfilmentStatus.Delivered),
                item("store-1", EAN_A, "MFN-A", 2, FulfilmentStatus.Delivered),
                item("store-1", EAN_A, "MFN-A", 7, FulfilmentStatus.Ordered),
                item("store-1", EAN_B, "MFN-B", 3, FulfilmentStatus.Ordered),
                item("store-1", "5900000000099", "MFN-Z", 9, FulfilmentStatus.Delivered)));

        // when
        Map<String, Long> qty = lookup.inStockByEan("store-1", List.of(EAN_A, EAN_B, EAN_C));

        // then
        assertThat(qty).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 6L));
    }

    @Test
    void itemOfAnotherProductWithTheSameCodeIsNotCounted() {
        // given
        when(stock.searchAllAvailable("store-1")).thenReturn(List.of(
                item("store-1", EAN_A, "MFN-A", 2, FulfilmentStatus.Delivered),
                item("store-1", EAN_B, "MFN-A", 5, FulfilmentStatus.Delivered)));

        // when
        Map<String, Long> qty = lookup.inStockByEan("store-1", List.of(EAN_A));

        // then
        assertThat(qty).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 2L));
    }

    @Test
    void itemWithoutEanIsNotCounted() {
        // given
        when(stock.searchAllAvailable("store-1")).thenReturn(List.of(
                item("store-1", null, "MFN-A", 4, FulfilmentStatus.Delivered),
                item("store-1", " ", "MFN-A", 3, FulfilmentStatus.Delivered),
                item("store-1", EAN_A, "MFN-A", 2, FulfilmentStatus.Delivered)));

        // when
        Map<String, Long> qty = lookup.inStockByEan("store-1", List.of(EAN_A, " "));

        // then
        assertThat(qty).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 2L));
    }

    @Test
    void nextPageWithinTheMinuteIsAnsweredWithoutAskingTheWarehouseAgain() {
        // given
        when(stock.searchAllAvailable("store-1")).thenReturn(List.of(
                item("store-1", EAN_A, "MFN-A", 2, FulfilmentStatus.Delivered),
                item("store-1", EAN_B, "MFN-B", 5, FulfilmentStatus.Delivered)));

        // when
        Map<String, Long> firstPage = lookup.inStockByEan("store-1", List.of(EAN_A));
        Map<String, Long> secondPage = lookup.inStockByEan("store-1", List.of(EAN_B, EAN_C));

        // then
        assertThat(firstPage).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 2L));
        assertThat(secondPage).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_B, 5L));
        verify(stock, times(1)).searchAllAvailable("store-1");
    }

    @Test
    void storesAreCachedSeparately() {
        // given
        when(stock.searchAllAvailable("store-1")).thenReturn(List.of(item("store-1", EAN_A, "MFN-A", 2, FulfilmentStatus.Delivered)));
        when(stock.searchAllAvailable("store-2")).thenReturn(List.of(item("store-2", EAN_A, "MFN-A", 8, FulfilmentStatus.Delivered)));

        // when
        Map<String, Long> first = lookup.inStockByEan("store-1", List.of(EAN_A));
        Map<String, Long> second = lookup.inStockByEan("store-2", List.of(EAN_A));

        // then
        assertThat(first).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 2L));
        assertThat(second).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 8L));
        verify(stock, times(1)).searchAllAvailable("store-1");
        verify(stock, times(1)).searchAllAvailable("store-2");
    }

    @Test
    void failedLoadIsNotCachedAndTheNextPageAsksAgain() {
        // given
        when(stock.searchAllAvailable("store-1"))
                .thenThrow(new IllegalStateException("scan failed"))
                .thenReturn(List.of(item("store-1", EAN_A, "MFN-A", 3, FulfilmentStatus.Delivered)));

        // when
        Map<String, Long> failed = lookup.inStockByEan("store-1", List.of(EAN_A));
        Map<String, Long> retried = lookup.inStockByEan("store-1", List.of(EAN_A));

        // then
        assertThat(failed).isEmpty();
        assertThat(retried).containsExactlyInAnyOrderEntriesOf(Map.of(EAN_A, 3L));
        verify(stock, times(2)).searchAllAvailable("store-1");
    }

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    private static WarehouseItemView item(String storeId, String ean, String mfn, int qty, FulfilmentStatus status) {
        return new WarehouseItemView(storeId, "item-" + ean + mfn + qty, "Produkt", ean, mfn, Price.fromNet(100.0),
                qty, status, ItemCondition.Sealed);
    }
}
