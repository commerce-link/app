package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProduct;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.Categories;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrderImporterScopeTest {

    private static final String MPN = "MPN-ONLY-S1";

    @Spy private CarrierDictionary carrierDictionary = new CarrierDictionary();
    @Mock private OrdersManager ordersManager;
    @Mock private pl.commercelink.pim.api.PimCatalog pimCatalog;
    @InjectMocks private MarketplaceOrderImporter importer;

    private String importedLineCategory(String storeId) {
        org.springframework.test.util.ReflectionTestUtils.setField(importer, "pimCatalog", ScopedPimCatalog.of(
                ScopedPimCatalog.storeEntry("s1-2", "s1", ScopedPimCatalog.STORE_GTIN, MPN, "Monitors")));
        Store store = new Store();
        store.setStoreId(storeId);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        MarketplaceCustomer.Address address = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, address, address);
        MarketplaceOrder order = new MarketplaceOrder("mp-order-1", customer,
                List.of(new MarketplaceProduct("Widget", MPN, new BigDecimal("100.00"), 1, BigDecimal.ZERO)),
                new MarketplaceOrder.Shipping(new BigDecimal("9.99"), "InPost", null, null), "BankTransfer", "txn-1");

        importer.importOrder(store, "Allegro", order);

        ArgumentCaptor<List<OrderItem>> items = ArgumentCaptor.forClass(List.class);
        verify(ordersManager).saveWithFulfilment(any(), items.capture());
        return items.getValue().stream().filter(i -> !i.isService()).findFirst().orElseThrow().getCategory();
    }

    @Test
    void lineCategoryComesFromTheStoresViewForTheOwningStore() {
        assertThat(importedLineCategory("s1")).isEqualTo("Monitors");
    }

    @Test
    void lineCategoryIsUncategorizedForAnotherStore() {
        assertThat(importedLineCategory("s2")).isEqualTo(Categories.UNCATEGORIZED);
    }
}
