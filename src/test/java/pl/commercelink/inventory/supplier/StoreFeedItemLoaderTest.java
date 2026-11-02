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
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.XmlItem;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
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

    private SupplierProviderDescriptor csvDescriptor(String name) {
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo(name));
        when(descriptor.feedFormat()).thenReturn(new FeedFormat.Csv(mock(CsvRowParser.class), ';'));
        return descriptor;
    }

    private SupplierProviderDescriptor xmlDescriptor(String name) {
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo(name));
        when(descriptor.feedFormat()).thenReturn(new FeedFormat.Xml(XmlItem.class, "Item"));
        return descriptor;
    }

    @Test
    void loadsCsvFeedFromStoreScopedKeyAndConvertsToLocalCurrency() {
        // given
        SupplierProviderDescriptor descriptor = csvDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(csvLoader.fetch(any(), eq(';'), eq("store-1"), named("Action"), eq(1000))).thenReturn(List.of(eurItem));

        // when
        List<InventoryItem> result = loader.load("store-1", "Action", descriptor, Map.of("EUR", 4.0));

        // then
        assertEquals(1, result.size());
        assertEquals("PLN", result.get(0).currency());
        verify(csvLoader).fetch(any(), eq(';'), eq("store-1"), named("Action"), eq(1000));
    }

    @Test
    void loadsXmlFeedFromStoreScopedKeyAndPassesPenalty() {
        // given
        SupplierProviderDescriptor descriptor = xmlDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(xmlLoader.load(any(), eq("Item"), any(), eq("store-1"), eq(1000))).thenReturn(List.of(eurItem));

        // when
        List<InventoryItem> result = loader.load("store-1", "Action-k7f3a9c2", descriptor, Map.of("EUR", 4.0));

        // then
        assertEquals(1, result.size());
        assertEquals("PLN", result.get(0).currency());
        verify(xmlLoader).load(any(), eq("Item"),
                argThat((SupplierInfo info) -> info.name().equals("Action-k7f3a9c2")), eq("store-1"), eq(1000));
    }

    @Test
    void dropsItemsWithoutAnExchangeRate() {
        // given
        SupplierProviderDescriptor descriptor = csvDescriptor("Action");
        InventoryItem eurItem = new InventoryItem("4711111111111", "MFN1", 100.0, "EUR", 5, 2, "Action", true, true, false);
        when(csvLoader.fetch(any(), eq(';'), eq("store-1"), named("Action"), anyInt())).thenReturn(List.of(eurItem));

        // when
        List<InventoryItem> result = loader.load("store-1", "Action", descriptor, Map.of());

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void csvRowsAreAssembledWithTheConnectionIdentity() {
        // given
        SupplierProviderDescriptor descriptor = csvDescriptor("Kosatec");

        // when
        loader.load("store-1", "Kosatec-k7f3a9c2", descriptor, Map.of("PLN", 1.0));

        // then
        verify(csvLoader).fetch(any(), eq(';'), eq("store-1"),
                argThat((SupplierInfo info) -> info.name().equals("Kosatec-k7f3a9c2") && info.accuracyScore() == 1),
                eq(1000));
    }

    @Test
    void manualFeedRowKeepsTheManualAccuracyScore() {
        // given
        SupplierProviderDescriptor descriptor = ManualSupplierDescriptor.forIdentity("manual-k7f3a9c2");

        // when
        loader.load("store-1", "manual-k7f3a9c2", descriptor, Map.of("PLN", 1.0));

        // then
        verify(csvLoader).fetch(any(), eq(';'), eq("store-1"),
                argThat((SupplierInfo info) -> info.name().equals("manual-k7f3a9c2")
                        && info.accuracyScore() == ManualSupplierInfos.ACCURACY_SCORE),
                eq(1000));
    }

    private static SupplierInfo named(String name) {
        return argThat((SupplierInfo info) -> info != null && info.name().equals(name));
    }
}
