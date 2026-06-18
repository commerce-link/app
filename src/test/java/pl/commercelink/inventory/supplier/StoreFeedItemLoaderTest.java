package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.XmlItem;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreFeedItemLoaderTest {

    @Mock
    private CsvProductFeedLoader csvLoader;
    @Mock
    private XmlProductFeedLoader xmlLoader;

    private StoreFeedItemLoader loader;

    @BeforeEach
    void setUp() {
        loader = new StoreFeedItemLoader(csvLoader, xmlLoader, 1000);
    }

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

    private SupplierDescriptor xmlDescriptor(String name) {
        SupplierDescriptor descriptor = mock(SupplierDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo(name));
        when(descriptor.feedFormat()).thenReturn(new FeedFormat.Xml(XmlItem.class, "Item"));
        return descriptor;
    }

    @Test
    void loadsCsvFeedFromStoreScopedKeyAndConvertsToLocalCurrency() {
        // given
        SupplierDescriptor descriptor = csvDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(csvLoader.fetch(any(), eq(';'), eq("store-1"), eq("Action"), eq(1000))).thenReturn(List.of(eurItem));

        // when
        List<InventoryItem> result = loader.load("store-1", descriptor, Map.of("EUR", 4.0));

        // then
        assertEquals(1, result.size());
        assertEquals("PLN", result.get(0).currency());
        verify(csvLoader).fetch(any(), eq(';'), eq("store-1"), eq("Action"), eq(1000));
    }

    @Test
    void loadsXmlFeedFromStoreScopedKeyAndPassesPenalty() {
        // given
        SupplierDescriptor descriptor = xmlDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(xmlLoader.load(any(), eq("Item"), any(), eq("store-1"), eq(1000))).thenReturn(List.of(eurItem));

        // when
        List<InventoryItem> result = loader.load("store-1", descriptor, Map.of("EUR", 4.0));

        // then
        assertEquals(1, result.size());
        assertEquals("PLN", result.get(0).currency());
        verify(xmlLoader).load(any(), eq("Item"), any(), eq("store-1"), eq(1000));
    }

    @Test
    void dropsItemsWithoutAnExchangeRate() {
        // given
        SupplierDescriptor descriptor = csvDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(csvLoader.fetch(any(), eq(';'), eq("store-1"), eq("Action"), anyInt())).thenReturn(List.of(eurItem));

        // when
        List<InventoryItem> result = loader.load("store-1", descriptor, Map.of());

        // then
        assertTrue(result.isEmpty());
    }
}
