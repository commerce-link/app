package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.orders.OrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfilmentGroupsGeneratorSkuLookupTest {

    private static final String EAN = "5900000000014";
    private static final String OTHER_EAN = "5900000000021";

    @Mock
    private InventoryView inventory;

    @Test
    void skuIsLookedUpOnlyByEan() {
        // given
        MatchedInventory byEan = offering(offer(EAN, "MFN-1", "Acme"));
        when(inventory.findByEan(EAN)).thenReturn(byEan);

        // when
        List<FulfilmentItem> items = generator().run(List.of(itemWithSku(EAN)));

        // then
        assertThat(items).extracting(i -> i.getSource().getProvider()).containsExactly("Acme");
    }

    @Test
    void skuWithAManufacturerCodeFindsNoOffers() {
        // given
        when(inventory.findByEan("MFN-1")).thenReturn(nothing());

        // when
        List<FulfilmentItem> items = generator().run(List.of(itemWithSku("MFN-1")));

        // then
        assertThat(items).isEmpty();
    }

    @Test
    void everyPreBuildPartIsLookedUpByEan() {
        // given
        MatchedInventory first = offering(offer(EAN, "MFN-1", "Acme"));
        MatchedInventory second = offering(offer(OTHER_EAN, "MFN-2", "Beta"));
        when(inventory.findByEan(EAN)).thenReturn(first);
        when(inventory.findByEan(OTHER_EAN)).thenReturn(second);

        // when
        List<FulfilmentItem> items = generator().run(List.of(itemWithSku(EAN + ":" + OTHER_EAN)));

        // then
        assertThat(items).extracting(i -> i.getSource().getProvider()).containsExactlyInAnyOrder("Acme", "Beta");
    }

    private FulfilmentGroupsGenerator generator() {
        return FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .build();
    }

    private static OrderItem itemWithSku(String sku) {
        return new OrderItem("order-1", "Category", "Product", 1, 199.99, sku, false);
    }

    private static InventoryItem offer(String ean, String mfn, String supplier) {
        return new InventoryItem(ean, mfn, 109.0, "PLN", 5, 2, supplier, true, true, false);
    }

    private static MatchedInventory offering(InventoryItem offer) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.getInventoryItems()).thenReturn(List.of(offer));
        return matched;
    }

    private static MatchedInventory nothing() {
        return MatchedInventory.empty(new InventoryKey());
    }
}
