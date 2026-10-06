package pl.commercelink.inventory.search;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferShippingTest {

    @Test
    void forItemOfUnknownSupplierIsUnknown() {
        // given
        SupplierRegistry registry = mock(SupplierRegistry.class);
        when(registry.exists("Nobody")).thenReturn(false);
        InventoryItem item = new InventoryItem("5901234567890", "MFN-1", 100.0, "PLN", 3, 1, "Nobody");

        // when
        OfferShipping shipping = OfferShipping.forItem(registry, item);

        // then
        assertThat(shipping).isEqualTo(OfferShipping.UNKNOWN);
    }

    @Test
    void forItemOfKnownSupplierQuotesPolishTermsForOneUnit() {
        // given
        SupplierRegistry registry = mock(SupplierRegistry.class);
        SupplierInfo info = mock(SupplierInfo.class);
        when(registry.exists("AB")).thenReturn(true);
        when(registry.get("AB")).thenReturn(info);
        when(info.shippingTermsFor("PL")).thenReturn(new ShippingTerms(2, new ShippingCostPolicy.FlatRate(500, 18)));
        InventoryItem item = new InventoryItem("5901234567890", "MFN-1", 100.0, "PLN", 3, 1, "AB");

        // when
        OfferShipping shipping = OfferShipping.forItem(registry, item);

        // then
        assertThat(shipping.known()).isTrue();
        assertThat(shipping.deliveryNet()).isEqualTo(18.0);
        assertThat(shipping.totalDays()).isEqualTo(3);
    }
}
