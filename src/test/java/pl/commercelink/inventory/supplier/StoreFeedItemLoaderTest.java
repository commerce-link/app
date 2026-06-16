package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreFeedItemLoaderTest {

    private final CsvProductFeedLoader csvLoader = mock(CsvProductFeedLoader.class);
    private final XmlProductFeedLoader xmlLoader = mock(XmlProductFeedLoader.class);
    private final StoreFeedItemLoader loader = new StoreFeedItemLoader(csvLoader, xmlLoader);

    private SupplierInfo supplierInfo(String name) {
        return new SupplierInfo(name, SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));
    }

    private SupplierDescriptor csvDescriptor(String name) {
        SupplierDescriptor descriptor = mock(SupplierDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo(name));
        when(descriptor.feedFormat()).thenReturn(new FeedFormat.Csv(mock(CsvRowParser.class), ';'));
        return descriptor;
    }

    @Test
    void loadsCsvFeedFromStoreScopedKeyAndConvertsToLocalCurrency() {
        SupplierDescriptor descriptor = csvDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(csvLoader.fetch(any(), eq(';'), eq("store-1"), eq("Action"))).thenReturn(List.of(eurItem));

        List<InventoryItem> result = loader.load("store-1", descriptor, Map.of("EUR", 4.0));

        assertEquals(1, result.size());
        assertEquals("PLN", result.get(0).currency());
    }

    @Test
    void dropsItemsWithoutAnExchangeRate() {
        SupplierDescriptor descriptor = csvDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(csvLoader.fetch(any(), eq(';'), eq("store-1"), eq("Action"))).thenReturn(List.of(eurItem));

        List<InventoryItem> result = loader.load("store-1", descriptor, Map.of());

        assertTrue(result.isEmpty());
    }
}
