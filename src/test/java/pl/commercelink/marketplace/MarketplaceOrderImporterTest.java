package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketplaceOrderImporterTest {

    private final MarketplaceOrderImporter importer = new MarketplaceOrderImporter();

    @Test
    void rendersPickupPointInShippingStreetLine() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska",
                new MarketplaceCustomer.PickupPoint("ALP123", "Paczkomat ALP123"));
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("ALP123 (Paczkomat ALP123), Prosta 1", shipping.getStreetAndNumber());
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
