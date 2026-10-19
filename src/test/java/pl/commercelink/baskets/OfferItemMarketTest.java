package pl.commercelink.baskets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferItemMarketTest {

    @Mock
    private MatchedInventory inventory;

    @Mock
    private InventoryItem lowestPricedItem;

    @Test
    void itemWithoutOffersHasNoMarketNumbers() {
        // given
        BasketItem item = new BasketItem();
        item.setName("Mysz");

        // when
        OfferItem offer = new OfferItem(item);

        // then
        assertThat(offer.isExistInInventory()).isFalse();
        assertThat(offer.getLowestCost()).isZero();
        assertThat(offer.getMedianCost()).isZero();
        assertThat(offer.getTotalQty()).isZero();
        assertThat(offer.getSupplierCount()).isZero();
    }

    @Test
    void itemWithOffersExposesLowestMedianStockAndSupplierCount() {
        // given
        BasketItem item = new BasketItem();
        item.setName("Mysz");
        when(lowestPricedItem.qty()).thenReturn(5);
        when(inventory.getLowestPrice()).thenReturn(Price.fromGross(338.00, 23));
        when(inventory.getLowestPricedInventoryItem()).thenReturn(lowestPricedItem);
        when(inventory.getMedianPrice()).thenReturn(Price.fromGross(349.00, 23));
        when(inventory.getMedianAvailableQty()).thenReturn(10);
        when(inventory.getMedianPriceSuppliers()).thenReturn(List.of("B"));
        when(inventory.getSuppliers()).thenReturn(List.of("A", "B", "C"));
        when(inventory.getTotalAvailableQty()).thenReturn(260L);

        // when
        OfferItem offer = new OfferItem(item, inventory);

        // then
        assertThat(offer.getLowestCost()).isEqualTo(338.00);
        assertThat(offer.getMedianCost()).isEqualTo(349.00);
        assertThat(offer.getTotalQty()).isEqualTo(260);
        assertThat(offer.getSupplierCount()).isEqualTo(3);
    }
}
