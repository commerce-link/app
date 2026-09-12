package pl.commercelink.baskets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasketTest {

    @Test
    @DisplayName("hoursToExpiry counts whole hours left until expiry")
    void hoursToExpiryCountsWholeHoursLeftUntilExpiry() {
        // given
        Basket basket = new Basket();
        basket.setExpiresAt(LocalDateTime.now().plusDays(2).plusHours(5).plusMinutes(30));

        // when
        long hours = basket.getHoursToExpiry();
        long days = basket.getDaysToExpiry();

        // then
        assertThat(hours).isEqualTo(53);
        assertThat(days).isEqualTo(2);
    }

    @Test
    @DisplayName("hoursToExpiry is zero for an already expired or undated basket")
    void hoursToExpiryIsZeroForExpiredOrUndatedBasket() {
        // given
        Basket expired = new Basket();
        expired.setExpiresAt(LocalDateTime.now().minusHours(3));
        Basket undated = new Basket();

        // when / then
        assertThat(expired.getHoursToExpiry()).isZero();
        assertThat(undated.getHoursToExpiry()).isZero();
        assertThat(undated.getDaysToExpiry()).isZero();
    }

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
    @DisplayName("removeBasketItem removes the item at the list index regardless of positions")
    void removeBasketItemRemovesItemAtListIndexRegardlessOfPositions() {
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
    @DisplayName("effective basket items take the first item of a variant group when the client has not chosen yet")
    void effectiveBasketItemsTakeFirstItemOfVariantGroupByDefault() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(basketItem("MFN-CPU", "CPU"), variant("MFN-SSD-1", "SSD"), variant("MFN-SSD-2", "SSD")));

        // when
        List<BasketItem> effective = basket.getEffectiveBasketItems();

        // then
        assertThat(effective).extracting(BasketItem::getMfn).containsExactly("MFN-CPU", "MFN-SSD-1");
    }

    @Test
    @DisplayName("selectVariant switches the effective item of the group and the total price")
    void selectVariantSwitchesEffectiveItemAndTotalPrice() {
        // given
        Basket basket = new Basket();
        BasketItem ssd1 = variant("MFN-SSD-1", "SSD");
        BasketItem ssd2 = variant("MFN-SSD-2", "SSD");
        ssd2.setUnitPrice(300.0);
        basket.setBasketItems(List.of(basketItem("MFN-CPU", "CPU"), ssd1, ssd2));

        // when
        basket.selectVariant("SSD", ssd2.getPosition());

        // then
        assertThat(basket.getEffectiveBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-CPU", "MFN-SSD-2");
        assertThat(ssd1.isVariantSelected()).isFalse();
        assertThat(ssd2.isVariantSelected()).isTrue();
        assertThat(basket.getTotalPrice()).isEqualTo(400.0);
    }

    @Test
    @DisplayName("selectVariant rejects a position that does not belong to the group")
    void selectVariantRejectsPositionOutsideTheGroup() {
        // given
        Basket basket = new Basket();
        BasketItem cpu = basketItem("MFN-CPU", "CPU");
        basket.setBasketItems(List.of(cpu, variant("MFN-SSD-1", "SSD"), variant("MFN-SSD-2", "SSD")));

        // when / then
        assertThatThrownBy(() -> basket.selectVariant("SSD", cpu.getPosition()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setBasketItems pulls split fragments of a variant group together behind its first item")
    void setBasketItemsJoinsSplitVariantGroupFragments() {
        // given
        Basket basket = new Basket();

        // when
        basket.setBasketItems(List.of(variant("MFN-SSD-1", "SSD"), basketItem("MFN-CPU", "CPU"), variant("MFN-SSD-2", "SSD")));

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-SSD-1", "MFN-SSD-2", "MFN-CPU");
        assertThat(basket.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("setBasketItems marks the first item of each variant group as selected when nothing is selected yet")
    void setBasketItemsMarksFirstItemOfEachGroupAsSelectedByDefault() {
        // given
        Basket basket = new Basket();
        BasketItem ssd1 = variant("MFN-SSD-1", "SSD");
        BasketItem ssd2 = variant("MFN-SSD-2", "SSD");
        BasketItem gpu1 = variant("MFN-GPU-1", "GPU");

        // when
        basket.setBasketItems(List.of(ssd1, ssd2, gpu1));

        // then
        assertThat(ssd1.isVariantSelected()).isTrue();
        assertThat(ssd2.isVariantSelected()).isFalse();
        assertThat(gpu1.isVariantSelected()).isTrue();
    }

    @Test
    @DisplayName("setBasketItems keeps the client's selection even when it is no longer the first item of the group")
    void setBasketItemsKeepsExistingSelectionRegardlessOfOrder() {
        // given
        Basket basket = new Basket();
        BasketItem ssd1 = variant("MFN-SSD-1", "SSD");
        BasketItem ssd2 = variant("MFN-SSD-2", "SSD");
        ssd2.setVariantSelected(true);

        // when
        basket.setBasketItems(List.of(ssd1, ssd2));

        // then
        assertThat(ssd1.isVariantSelected()).isFalse();
        assertThat(ssd2.isVariantSelected()).isTrue();
        assertThat(basket.getEffectiveBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-SSD-2");
    }

    @Test
    @DisplayName("setBasketItems clears a stale selection flag on an item that left its group")
    void setBasketItemsClearsSelectionOnUngroupedItem() {
        // given
        Basket basket = new Basket();
        BasketItem loose = basketItem("MFN-A");
        loose.setVariantSelected(true);

        // when
        basket.setBasketItems(List.of(loose));

        // then
        assertThat(loose.isVariantSelected()).isFalse();
    }

    @Test
    @DisplayName("addBasketItem inserts a same-category item after the whole variant group instead of inside it")
    void addBasketItemInsertsAfterWholeVariantGroup() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(variant("MFN-SSD-1", "SSD"), variant("MFN-SSD-2", "SSD"), basketItem("MFN-CPU", "CPU")));
        BasketItem loose = basketItem("MFN-SSD-3");

        // when
        basket.addBasketItem(loose);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-SSD-1", "MFN-SSD-2", "MFN-SSD-3", "MFN-CPU");
        assertThat(loose.isInVariantGroup()).isFalse();
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

    @Test
    @DisplayName("createOfferUrl uses the client offer path for offers created on or after 10 September 2026")
    void createOfferUrlUsesClientPathForOffersCreatedSinceCutoff() {
        // given
        Basket atCutoff = offer(LocalDateTime.of(2026, 9, 10, 0, 0));
        Basket afterCutoff = offer(LocalDateTime.of(2026, 11, 3, 14, 15));

        // when / then
        assertThat(atCutoff.createOfferUrl("https://app.example")).isEqualTo("https://app.example/store/store-1/client/offer/offer-1");
        assertThat(afterCutoff.createOfferUrl("https://app.example")).isEqualTo("https://app.example/store/store-1/client/offer/offer-1");
    }

    @Test
    @DisplayName("createOfferUrl keeps the individual offer path for offers created before 10 September 2026 or without a creation date")
    void createOfferUrlKeepsIndividualPathForOffersCreatedBeforeCutoff() {
        // given
        Basket beforeCutoff = offer(LocalDateTime.of(2026, 9, 9, 23, 59, 59));
        Basket undated = offer(null);

        // when / then
        assertThat(beforeCutoff.createOfferUrl("https://app.example")).isEqualTo("https://app.example/store/store-1/individual/offer/offer-1");
        assertThat(undated.createOfferUrl("https://app.example")).isEqualTo("https://app.example/store/store-1/individual/offer/offer-1");
    }

    private Basket offer(LocalDateTime createdAt) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId("offer-1");
        basket.setCreatedAt(createdAt);
        return basket;
    }

    private BasketItem basketItem(String mfn) {
        return basketItem(mfn, "Laptops");
    }

    private BasketItem basketItem(String mfn, String category) {
        return new BasketItem("pim-1", "Product", mfn,
                category, 100.0, 0, 1, null, 3, false);
    }

    private BasketItem variant(String mfn, String groupId) {
        BasketItem item = basketItem(mfn);
        item.setVariantGroupId(groupId);
        return item;
    }
}
