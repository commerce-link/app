package pl.commercelink.baskets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import static org.assertj.core.api.Assertions.assertThat;

class OfferItemTest {

    @Test
    @DisplayName("offer item without basket item has null position and zero unit price")
    void offerItemWithoutBasketItemHasNullPositionAndZeroUnitPrice() {
        // given
        OfferItem emptyRow = new OfferItem(0);

        // when / then
        assertThat(emptyRow.getPosition()).isNull();
        assertThat(emptyRow.getUnitPrice()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("offer item with basket item delegates position and unit price to it")
    void offerItemWithBasketItemDelegatesPositionAndUnitPriceToIt() {
        // given
        BasketItem basketItem = new BasketItem("pim-1", "Test Product", "MFN-1",
                ProductCategory.Laptops, 199.99, 0, 1, null, 3, false);
        basketItem.setPosition(4);
        OfferItem offerItem = new OfferItem(0, basketItem);

        // when / then
        assertThat(offerItem.getPosition()).isEqualTo(4);
        assertThat(offerItem.getUnitPrice()).isEqualTo(199.99);
    }

}
