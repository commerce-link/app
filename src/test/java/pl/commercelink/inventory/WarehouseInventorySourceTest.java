package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    private static WarehouseItemView stock(String ean, ItemCondition condition) {
        return new WarehouseItemView(STORE_ID, "item-" + ean + "-" + condition, "Widget", ean, "M1", Price.fromNet(1200.0, 0.23), 3,
                FulfilmentStatus.Delivered, condition);
    }

    private MatchedInventory accumulator(InventoryKey lookupKey) {
        return new MatchedInventory(lookupKey.copy(), taxonomyCache, supplierRegistry);
    }

    private MatchedInventory merged(WarehouseInventorySource source, InventoryKey lookupKey) {
        MatchedInventory result = accumulator(lookupKey);
        source.mergeInto(result, lookupKey);
        return result;
    }

    @Test
    void appendsWarehouseOffersForLookupEans() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(stock("5900000000014", ItemCondition.Sealed)));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        MatchedInventory result = merged(source, InventoryKey.fromEan("5900000000014"));

        // then
        assertThat(result.getSuppliers()).containsExactly("Warehouse");
    }

    @Test
    void itemsAreFoundByEveryEanOfTheKey() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(
                stock("5900000000014", ItemCondition.Sealed), stock("5900000000021", ItemCondition.Sealed)));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        MatchedInventory result = merged(source, new InventoryKey(Set.of("5900000000014", "5900000000021"), Set.of("M1")));

        // then
        assertThat(result.getInventoryItemsFromSupplier("Warehouse")).hasSize(2);
    }

    @Test
    void itemsAreNotLookedUpByManufacturerCode() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(stock("5900000000014", ItemCondition.Sealed)));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        MatchedInventory result = merged(source, new InventoryKey("5900000000021", "M1"));

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void mergesNothingWhenWarehouseHasNoStock() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of());
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        MatchedInventory result = merged(source, InventoryKey.fromEan("5900000000014"));

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }

    @Test
    void itemsThatAreNotSealedAreNotOffered() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(
                stock("5900000000014", ItemCondition.OpenBox), stock("5900000000014", ItemCondition.Damaged)));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        MatchedInventory result = merged(source, InventoryKey.fromEan("5900000000014"));

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void anItemWithoutAnEanIsSkipped() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(
                stock(null, ItemCondition.Sealed), stock("5900000000014", ItemCondition.Sealed)));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        MatchedInventory result = merged(source, InventoryKey.fromEan("5900000000014"));

        // then
        assertThat(result.getInventoryItemsFromSupplier("Warehouse")).hasSize(1);
    }

    @Test
    void theWarehouseIsReadOnceHoweverManyProductsAreLookedUp() {
        // given
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(stock("5900000000014", ItemCondition.Sealed)));
        WarehouseInventorySource source = new WarehouseInventorySource(STORE_ID, stockQueryService);

        // when
        for (int i = 0; i < 500; i++) {
            merged(source, InventoryKey.fromEan("59000000" + String.format("%05d", i)));
        }
        MatchedInventory inStock = merged(source, InventoryKey.fromEan("5900000000014"));

        // then
        assertThat(inStock.getSuppliers()).containsExactly("Warehouse");
        verify(stockQueryService, times(1)).searchAllAvailable(STORE_ID);
        verifyNoMoreInteractions(stockQueryService);
    }

    @Test
    void aSourceNobodyAsksDoesNotReadTheWarehouse() {
        // when
        new WarehouseInventorySource(STORE_ID, stockQueryService);

        // then
        verifyNoInteractions(stockQueryService);
    }
}
