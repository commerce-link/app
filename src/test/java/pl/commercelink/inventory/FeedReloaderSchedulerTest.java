package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.CsvProductFeedLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.XmlProductFeedLoader;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.XmlItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedReloaderSchedulerTest {

    @Mock
    private Inventory inventory;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private CsvProductFeedLoader csvProductFeedLoader;
    @Mock
    private XmlProductFeedLoader xmlProductFeedLoader;
    @Mock
    private ExchangeRates exchangeRates;
    @InjectMocks
    private FeedReloaderScheduler scheduler;

    @Test
    void keepsPreviousInventoryAndMarksSeenWhenAFeedYieldsNoItems() {
        // given a supplier whose feed is newer than the last load but parses to nothing (e.g. rejected feed)
        LocalDateTime feedModified = LocalDateTime.now();
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(new SupplierInfo("acme", SupplierType.Distributor, 0, "PL", null));
        when(descriptor.feedFormat()).thenReturn(new FeedFormat.Xml(StubXmlItem.class, "product"));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        when(inventoryRepository.getLatestModifiedPerSupplier()).thenReturn(Map.of("acme", feedModified));
        when(inventory.getLastUpdateDate("acme")).thenReturn(feedModified.minusDays(1));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(xmlProductFeedLoader.load(any(), any(), any())).thenReturn(List.of());

        // when
        scheduler.reloadIfNewFeedsAvailable();

        // then the supplier's inventory is not wiped, and the feed version is marked seen so it is
        // not re-parsed every cycle until a newer feed file arrives
        verify(inventory, never()).update(any());
        verify(inventory).markSeen("acme", feedModified);
    }

    @Test
    void updatesInventoryWithoutMarkingSeenWhenAFeedYieldsItems() {
        // given a supplier whose newer feed parses to at least one item
        LocalDateTime feedModified = LocalDateTime.now();
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(new SupplierInfo("acme", SupplierType.Distributor, 0, "PL", null));
        when(descriptor.feedFormat()).thenReturn(new FeedFormat.Xml(StubXmlItem.class, "product"));
        when(supplierProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        when(inventoryRepository.getLatestModifiedPerSupplier()).thenReturn(Map.of("acme", feedModified));
        when(inventory.getLastUpdateDate("acme")).thenReturn(feedModified.minusDays(1));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        InventoryItem item = new InventoryItem("5900000000001", "MFN-1", 10.0, "PLN", 5, 1, "acme", true, true, false);
        when(xmlProductFeedLoader.load(any(), any(), any())).thenReturn(List.of(item));

        // when
        scheduler.reloadIfNewFeedsAvailable();

        // then the supplier's inventory is updated and the feed is not marked as skipped
        verify(inventory).update(argThat(updates -> updates.containsKey("acme")));
        verify(inventory, never()).markSeen(eq("acme"), any());
    }

    static class StubXmlItem implements XmlItem {
        @Override
        public String getEan() {
            return null;
        }

        @Override
        public String getMfn() {
            return null;
        }

        @Override
        public String getBrand() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getCategory() {
            return null;
        }

        @Override
        public double getNetPrice() {
            return 0;
        }

        @Override
        public int getQty() {
            return 0;
        }

        @Override
        public String getCurrency() {
            return null;
        }

        @Override
        public ParsedRow toParsedRow(SupplierInfo supplierInfo) {
            return new ParsedRow(null, null);
        }
    }
}
