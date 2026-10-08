package pl.commercelink.pricelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceSnapshotGeneratorTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final String STORE_MESSAGE = "{\"storeId\":\"store-1\",\"date\":\"2026-10-07\"}";

    @Mock
    private Inventory inventory;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private PriceDataFanOut fanOut;
    @Mock
    private StoreActivity storeActivity;
    @Mock
    private InventoryView view;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private DailyPriceSnapshotGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DailyPriceSnapshotGenerator(inventory, fileStorage, fanOut, storeActivity, "datalake", "stores");
    }

    @Test
    void globalRunStoresTheSnapshotInTheDatalake() throws Exception {
        // given
        when(inventory.withGlobalData()).thenReturn(view);
        when(view.findAllWithPimId()).thenReturn(List.of(offerFromTwoSuppliers()));

        // when
        generator.handleMessage("{\"date\":\"2026-10-07\"}");

        // then
        verify(fileStorage).put(eq("datalake"), eq("daily-price-snapshot/2026-10-07.csv"), any(byte[].class));
    }

    @Test
    void globalRunQueuesTheStoresBeforeItsOwnWork() throws Exception {
        // given
        when(inventory.withGlobalData()).thenReturn(view);
        when(view.findAllWithPimId()).thenReturn(List.of(offerFromTwoSuppliers()));

        // when
        generator.handleMessage("");

        // then
        InOrder inOrder = inOrder(fanOut, fileStorage);
        inOrder.verify(fanOut).toActiveStores(eq(DailyPriceSnapshotGenerator.QUEUE), any(LocalDate.class));
        inOrder.verify(fileStorage).put(eq("datalake"), anyString(), any(byte[].class));
    }

    @Test
    void failingGlobalSnapshotStillLeavesTheStoresQueued() {
        // given
        when(inventory.withGlobalData()).thenThrow(new IllegalStateException("inventory unavailable"));

        // when / then
        assertThrows(IllegalStateException.class, () -> generator.handleMessage(""));
        verify(fanOut).toActiveStores(eq(DailyPriceSnapshotGenerator.QUEUE), any(LocalDate.class));
    }

    @Test
    void storeRunUsesTheStoreViewAndStoresItUnderTheStore() throws Exception {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(true);
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(view);
        when(view.findAllWithPimId()).thenReturn(List.of(offerFromTwoSuppliers()));

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verify(fileStorage).put(eq("stores"), eq("store-1/daily-price-snapshot/2026-10-07.csv"), any(byte[].class));
        verify(inventory, never()).withGlobalData();
        verifyNoInteractions(fanOut);
    }

    @Test
    void storeRunOfInactiveStoreDoesNothing() throws Exception {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(false);

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verifyNoInteractions(inventory, fileStorage, fanOut);
    }

    @Test
    void storeWithoutPricedOffersGetsNoFile() throws Exception {
        // given
        when(storeActivity.isActive(STORE_ID)).thenReturn(true);
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(view);
        when(view.findAllWithPimId()).thenReturn(List.of());

        // when
        generator.handleMessage(STORE_MESSAGE);

        // then
        verifyNoInteractions(fileStorage);
    }

    private MatchedInventory offerFromTwoSuppliers() {
        return new MatchedInventory(new InventoryKey("pim-1"), List.of(
                new InventoryItem("5900000000001", "MFN-1", 100.0, "PLN", 5, 1, "Alpha"),
                new InventoryItem("5900000000001", "MFN-1", 110.0, "PLN", 3, 1, "Beta")),
                taxonomyCache, supplierRegistry);
    }
}
