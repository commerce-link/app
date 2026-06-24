package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void appendsWarehouseOffersForLookupProductCodes() {
        // given
        WarehouseItemView view = mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(item("E1", "M1", "Warehouse", 1200.0));
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), any())).thenReturn(List.of(view));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);
        InventoryKey lookupKey = InventoryKey.fromMfn("M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("Warehouse");
    }

    @Test
    void mergesNothingWhenWarehouseHasNoStock() {
        // given
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), any())).thenReturn(List.of());
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);
        InventoryKey lookupKey = InventoryKey.fromMfn("M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }
}
