package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseInventorySourceTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StockQueryService stockQueryService;

    private InventoryItem item(String ean, String mfn, String supplier, double price) {
        return new InventoryItem(ean, mfn, price, "PLN", 5, 1, supplier, true, true, false);
    }

    private MatchedInventory accumulator(InventoryKey lookupKey) {
        return new MatchedInventory(lookupKey.copy(), taxonomyCache, supplierRegistry);
    }

    @Test
    void appendsWarehouseOffersForLookupEans() {
        // given
        WarehouseItemView view = mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(item("5900000000014", "M1", "Warehouse", 1200.0));
        when(stockQueryService.searchAvailableByEans(eq(STORE_ID), argThat(eans -> eans.contains("5900000000014")))).thenReturn(List.of(view));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);
        InventoryKey lookupKey = InventoryKey.fromEan("5900000000014");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("Warehouse");
    }

    @Test
    void itemsAreFoundByEveryEanOfTheKey() {
        // given
        WarehouseItemView view = mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(item("5900000000014", "M1", "Warehouse", 1200.0));
        when(stockQueryService.searchAvailableByEans(eq(STORE_ID),
                argThat(eans -> eans.containsAll(Set.of("5900000000014", "5900000000021"))))).thenReturn(List.of(view));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);
        InventoryKey lookupKey = new InventoryKey(Set.of("5900000000014", "5900000000021"), Set.of("M1"));
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("Warehouse");
    }

    @Test
    void itemsAreNotLookedUpByManufacturerCode() {
        // given
        when(stockQueryService.searchAvailableByEans(eq(STORE_ID), any())).thenReturn(List.of());
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);
        InventoryKey lookupKey = new InventoryKey("5900000000021", "M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void mergesNothingWhenWarehouseHasNoStock() {
        // given
        when(stockQueryService.searchAvailableByEans(eq(STORE_ID), any())).thenReturn(List.of());
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);
        InventoryKey lookupKey = InventoryKey.fromEan("5900000000014");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }
}
