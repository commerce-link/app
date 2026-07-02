package pl.commercelink.baskets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BasketTest {

    @Test
    @DisplayName("setBasketItems assigns zero-based positions following the list order")
    void setBasketItemsAssignsZeroBasedPositionsFollowingListOrder() {
        // given
        Basket basket = new Basket();
        BasketItem first = basketItem("MFN-A");
        BasketItem second = basketItem("MFN-B");

        // when
        basket.setBasketItems(List.of(first, second));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("setBasketItems overwrites stale positions restoring the list order invariant")
    void setBasketItemsOverwritesStalePositionsRestoringListOrderInvariant() {
        // given
        Basket basket = new Basket();
        BasketItem first = basketItem("MFN-A");
        first.setPosition(5);
        BasketItem second = basketItem("MFN-B");

        // when
        basket.setBasketItems(List.of(first, second));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("addBasketItem appends the item with the next list index as position")
    void addBasketItemAppendsItemWithNextListIndexAsPosition() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A"), basketItem("MFN-B")));
        BasketItem added = basketItem("MFN-C");

        // when
        basket.addBasketItem(added);

        // then
        assertThat(added.getPosition()).isEqualTo(2);
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("removeBasketItem removes the item at index and reindexes remaining positions")
    void removeBasketItemRemovesItemAtIndexAndReindexesRemainingPositions() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A"), basketItem("MFN-B"), basketItem("MFN-C")));

        // when
        basket.removeBasketItem(1);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-C");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("builder assigns zero-based positions after filtering out incomplete items")
    void builderAssignsZeroBasedPositionsAfterFilteringIncompleteItems() {
        // given
        BasketItem first = basketItem("MFN-A");
        BasketItem incomplete = new BasketItem("pim-2", "", "MFN-X",
                ProductCategory.Laptops, 100.0, 0, 1, null, 3, false);
        BasketItem second = basketItem("MFN-B");

        // when
        Basket basket = new Basket.Builder("store-1")
                .withBasketItems(List.of(first, incomplete, second))
                .build();

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-B");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("deepCopy carries basket item positions over to the copy")
    void deepCopyCarriesBasketItemPositionsOverToTheCopy() {
        // given
        Basket basket = new Basket.Builder("store-1")
                .withBasketItems(List.of(basketItem("MFN-A"), basketItem("MFN-B")))
                .build();

        // when
        Basket copy = basket.deepCopy(" - Copy", BasketType.Offer);

        // then
        assertThat(copy.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    private BasketItem basketItem(String mfn) {
        return new BasketItem("pim-1", "Product", mfn,
                ProductCategory.Laptops, 100.0, 0, 1, null, 3, false);
    }
}
