package pl.commercelink.inventory.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferShippingTest {

    @Mock private SupplierRegistry registry;

    @Test
    void forItemOfUnknownSupplierIsUnknown() {
        // given
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
        ShippingTerms terms = new ShippingTerms(2, new ShippingCostPolicy.FlatRate(500, 18));
        when(registry.exists("AB")).thenReturn(true);
        when(registry.get("AB")).thenReturn(new SupplierInfo("AB", SupplierType.Distributor, 1, "PL", new ShippingPolicy(terms)));
        InventoryItem item = new InventoryItem("5901234567890", "MFN-1", 100.0, "PLN", 3, 1, "AB");

        // when
        OfferShipping shipping = OfferShipping.forItem(registry, item);

        // then
        assertThat(shipping.known()).isTrue();
        assertThat(shipping.deliveryNet()).isEqualTo(18.0);
        assertThat(shipping.totalDays()).isEqualTo(3);
    }
}
