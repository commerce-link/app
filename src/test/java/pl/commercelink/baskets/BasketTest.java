package pl.commercelink.baskets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;
import java.util.Map;

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
    @DisplayName("addBasketItem inserts the item under the last item of the same category and shifts the rest")
    void addBasketItemInsertsItemUnderLastItemOfSameCategoryAndShiftsTheRest() {
        // given
        Basket basket = new Basket();
        BasketItem laptop = basketItem("MFN-A", "Laptops");
        BasketItem cpu = basketItem("MFN-B", "CPU");
        basket.setBasketItems(List.of(laptop, cpu));
        BasketItem addedLaptop = basketItem("MFN-C", "Laptops");

        // when
        basket.addBasketItem(addedLaptop);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-C", "MFN-B");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("addBasketItem appends the item at the end when no item of its category exists")
    void addBasketItemAppendsItemAtTheEndWhenNoItemOfItsCategoryExists() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A", "Laptops"), basketItem("MFN-B", "Laptops")));
        BasketItem addedCpu = basketItem("MFN-C", "CPU");

        // when
        basket.addBasketItem(addedCpu);

        // then
        assertThat(addedCpu.getPosition()).isEqualTo(2);
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-B", "MFN-C");
    }

    @Test
    @DisplayName("addBasketItemInCategoryOrder inserts a new category according to catalog sequence numbers")
    void addBasketItemInCategoryOrderInsertsNewCategoryAccordingToCatalogSequenceNumbers() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A", "CPU"), basketItem("MFN-B", "GPU")));
        BasketItem addedMotherboard = basketItem("MFN-C", "Motherboard");

        // when
        basket.addBasketItemInCategoryOrder(addedMotherboard, Map.of("CPU", 1, "Motherboard", 2, "GPU", 3));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-C", "MFN-B");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("addBasketItemInCategoryOrder places the item under existing same-category items over catalog order")
    void addBasketItemInCategoryOrderPlacesItemUnderExistingSameCategoryItemsOverCatalogOrder() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A", "GPU"), basketItem("MFN-B", "CPU")));
        BasketItem addedCpu = basketItem("MFN-C", "CPU");

        // when
        basket.addBasketItemInCategoryOrder(addedCpu, Map.of("CPU", 1, "GPU", 2));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-B", "MFN-C");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("addBasketItemInCategoryOrder falls back to the end when the category is unknown to the catalog")
    void addBasketItemInCategoryOrderFallsBackToTheEndWhenCategoryIsUnknownToTheCatalog() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A", "CPU"), basketItem("MFN-B", "GPU")));
        BasketItem addedRam = basketItem("MFN-C", "Memory");

        // when
        basket.addBasketItemInCategoryOrder(addedRam, Map.of("CPU", 1, "GPU", 2));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-B", "MFN-C");
        assertThat(addedRam.getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("removeBasketItem removes the item at index and leaves a gap in positions")
    void removeBasketItemRemovesItemAtIndexAndLeavesAGapInPositions() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A"), basketItem("MFN-B"), basketItem("MFN-C")));

        // when
        basket.removeBasketItem(1);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A", "MFN-C");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 2);
    }

    @Test
    @DisplayName("removeBasketItem removes the item at the list index even when the position order differs")
    void removeBasketItemRemovesItemAtListIndexWhenPositionOrderDiffers() {
        // given
        Basket basket = new Basket();
        BasketItem first = basketItem("MFN-A");
        first.setPosition(1);
        BasketItem second = basketItem("MFN-B");
        second.setPosition(0);
        basket.getBasketItems().addAll(List.of(first, second));

        // when
        basket.removeBasketItem(0);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-B");
    }

    @Test
    @DisplayName("setBasketItems places services into the service band starting at 800")
    void setBasketItemsPlacesServicesIntoServiceBand() {
        // given
        Basket basket = new Basket();
        BasketItem service = BasketItem.shipping("Dostawa", 20.0);

        // when
        basket.setBasketItems(List.of(service));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(800);
    }

    @Test
    @DisplayName("setBasketItems assigns products from zero and services from the service band independently")
    void setBasketItemsAssignsProductsFromZeroAndServicesFromServiceBand() {
        // given
        Basket basket = new Basket();
        BasketItem product = basketItem("MFN-A");
        BasketItem service = BasketItem.shipping("Dostawa", 20.0);

        // when
        basket.setBasketItems(List.of(product, service));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 800);
    }

    @Test
    @DisplayName("addBasketItem appends a service into the next free slot of the service band")
    void addBasketItemAppendsServiceIntoServiceBand() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-A"), BasketItem.shipping("Dostawa", 20.0)));
        BasketItem addedService = BasketItem.shipping("Montaz", 30.0);

        // when
        basket.addBasketItem(addedService);

        // then
        assertThat(addedService.getPosition()).isEqualTo(801);
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 800, 801);
    }

    @Test
    @DisplayName("builder assigns zero-based positions after filtering out incomplete items")
    void builderAssignsZeroBasedPositionsAfterFilteringIncompleteItems() {
        // given
        BasketItem first = basketItem("MFN-A");
        BasketItem incomplete = new BasketItem("pim-2", "", "MFN-X",
                "Laptops", 100.0, 0, 1, null, 3, false);
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
        return basketItem(mfn, "Laptops");
    }

    private BasketItem basketItem(String mfn, String category) {
        return new BasketItem("pim-1", "Product", mfn,
                category, 100.0, 0, 1, null, 3, false);
    }
}
