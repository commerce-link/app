package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.stores.Integration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.DeliveryFormCarrier;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MarketplaceOrderImporterTest {

    @Spy
    private CarrierDictionary carrierDictionary = dictionaryForMorele();

    @InjectMocks
    private MarketplaceOrderImporter importer;

    private static CarrierDictionary dictionaryForMorele() {
        CarrierDictionary dictionary = new CarrierDictionary();
        dictionary.setCarriers(Map.of("Morele", Map.of("furgonetka", "{\"2\":\"InPost\",\"6\":\"Zabka\"}")));
        return dictionary;
    }

    private static ShippingConfiguration configurationDeclaring(String source, String deliveryForm, String carrier) {
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.setDeliveryFormCarriers(List.of(new DeliveryFormCarrier(source, deliveryForm, carrier)));
        return configuration;
    }

    private static Store storeShippingWith(String shippingProvider) {
        Store store = new Store();
        store.setIntegrations(List.of(new Integration(IntegrationType.SHIPPING_PROVIDER, shippingProvider)));
        return store;
    }

    @Test
    void keepsTheDeliveryStreetIntactForPointOrders() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("Prosta 1", shipping.getStreetAndNumber());
    }

    @Test
    void rendersRawStreetWhenNoPickupPoint() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("Prosta 1", shipping.getStreetAndNumber());
    }

    @Test
    void toleratesNullShippingName() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                null, "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("", shipping.getName());
        assertEquals("", shipping.getSurname());
    }

    @Test
    void storesTheShippingProviderCarrierNameInsteadOfTheMarketplaceCode() {
        // when / then
        assertEquals("InPost", importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", "2"));
        assertEquals("Zabka", importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", "6"));
    }

    @Test
    void keepsTheMarketplaceValueWhenNothingMapsIt() {
        // when / then
        assertEquals("99", importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", "99"));
        assertEquals("2", importer.toCarrierName(storeShippingWith("apaczka"), "Morele", "2"));
        assertNull(importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", null));
    }

    @Test
    void leavesTheCarrierUnsetForAMarketplaceTheDictionaryDoesNotKnow() {
        // when / then
        assertNull(importer.toCarrierName(storeShippingWith("furgonetka"), "Ceneo",
                "Poczta Polska, P\u0142atno\u015b\u0107 z g\u00f3ry, List polecony ekonomiczny"));
    }

    @Test
    void usesTheCarrierTheMerchantDeclaredForTheDeliveryForm() {
        // given
        Store store = storeShippingWith("furgonetka");
        store.setShippingConfiguration(configurationDeclaring(
                "Ceneo", "Poczta Polska, P\u0142atno\u015b\u0107 z g\u00f3ry, List polecony ekonomiczny", "Poczta Polska"));

        // when / then
        assertEquals("Poczta Polska", importer.toCarrierName(store, "Ceneo",
                "Poczta Polska, P\u0142atno\u015b\u0107 z g\u00f3ry, List polecony ekonomiczny"));
    }

    @Test
    void matchesTheDeclaredDeliveryFormExactlyRatherThanBySubstring() {
        // given
        Store store = storeShippingWith("furgonetka");
        store.setShippingConfiguration(configurationDeclaring("Ceneo", "Kurier DPD", "DPD"));

        // when / then
        assertNull(importer.toCarrierName(store, "Ceneo", "Kurier DPD ekspres"));
    }

    @Test
    void ignoresADeclarationMadeForAnotherMarketplace() {
        // given
        Store store = storeShippingWith("furgonetka");
        store.setShippingConfiguration(configurationDeclaring("Empik", "Kurier DPD", "DPD"));

        // when / then
        assertNull(importer.toCarrierName(store, "Ceneo", "Kurier DPD"));
    }

    @Test
    void theDeclarationWinsOverTheGlobalDictionary() {
        // given
        Store store = storeShippingWith("furgonetka");
        store.setShippingConfiguration(configurationDeclaring("Morele", "2", "Orlen"));

        // when / then
        assertEquals("Orlen", importer.toCarrierName(store, "Morele", "2"));
    }

    @Test
    void toleratesAStoreWithoutAShippingProvider() {
        // when / then
        assertEquals("2", importer.toCarrierName(new Store(), "Morele", "2"));
    }

    @Test
    void toleratesNullBillingName() {
        // given
        MarketplaceCustomer.Address billingAddress = new MarketplaceCustomer.Address(
                null, "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, billingAddress, billingAddress);

        // when
        BillingDetails billing = importer.toBillingDetails(customer);

        // then
        assertEquals("", billing.getName());
        assertEquals("", billing.getSurname());
    }
}
