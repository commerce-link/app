package pl.commercelink.baskets;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.taxonomy.Taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasketItemTest {

    @Test
    void itemFromPricelistRowCarriesCategoryAsPlainString() {
        // given
        AvailabilityAndPrice row = pricelistRow("Usługi dodatkowe");

        // when
        BasketItem item = BasketItem.of(row, 1, "catalog-1", false);

        // then
        assertThat(item.getCategory()).isEqualTo("Usługi dodatkowe");
    }

    @Test
    void ofCopiesServiceFlagFromPricelistRow() {
        // given
        AvailabilityAndPrice serviceRow = new AvailabilityAndPrice("pim-1", "EAN", "MFN", "Brand",
                "Label", "Montaż PC", "Usługi dodatkowe", 100, 1, 1, 100, true);

        // when
        BasketItem item = BasketItem.of(serviceRow, 1, "cat-1", false);

        // then
        assertThat(item.isService()).isTrue();
    }

    @Test
    void ofCopiesBrandFromPricelistRow() {
        // given
        AvailabilityAndPrice row = new AvailabilityAndPrice("pim-1", "EAN", "MFN", "Acme",
                "Label", "Karta graficzna", "Karty graficzne", 100, 1, 1, 100, false);

        // when
        BasketItem item = BasketItem.of(row, 1, "cat-1", false);

        // then
        assertThat(item.getBrand()).isEqualTo("Acme");
    }

    @Test
    void pricelistItemGetsTheEanAsSku() {
        // given
        AvailabilityAndPrice row = new AvailabilityAndPrice("pim-1", "5901234123457", "MZ-V9P1T0BW", "Samsung",
                "Label", "Dysk", "Dyski", 100, 1, 1, 100, false);

        // when
        BasketItem item = BasketItem.of(row, 1, "cat-1", false);

        // then
        assertThat(item.getSku()).isEqualTo("5901234123457");
    }

    @Test
    void skuIsTheEanWhateverTheManufacturerCode() {
        // given
        AvailabilityAndPrice row = new AvailabilityAndPrice("pim-1", "5901234123457", "#1xA-1|2xB-2",
                "Acme", "Label", "Zestaw", "Zestawy", 100, 1, 1, 100, false);

        // when
        BasketItem item = BasketItem.of(row, 1, "cat-1", false);

        // then
        assertThat(item.getSku()).isEqualTo("5901234123457");
    }

    @Test
    void compositeEanGoesToSkuAsItIs() {
        // given
        AvailabilityAndPrice set = new AvailabilityAndPrice("pim-1", "#1x5901234123464|2x5901234123471", "ZESTAW-1",
                "Acme", "Label", "Zestaw", "Zestawy", 100, 1, 1, 100, false);
        AvailabilityAndPrice preBuild = new AvailabilityAndPrice("pim-2", "5901234123464:5901234123471", "PC-1",
                "Acme", "Label", "Komputer", "Komputery", 100, 1, 1, 100, false);

        // when
        BasketItem setItem = BasketItem.of(set, 1, "cat-1", false);
        BasketItem preBuildItem = BasketItem.of(preBuild, 1, "cat-1", false);

        // then
        assertThat(setItem.getSku()).isEqualTo("#1x5901234123464|2x5901234123471");
        assertThat(preBuildItem.getSku()).isEqualTo("5901234123464:5901234123471");
    }

    @Test
    void inventoryItemGetsTheEanOfItsTaxonomy() {
        // given
        MatchedInventory matched = offeredProduct(new Taxonomy("5901234123457", "MZ-V9P1T0BW", "Samsung", "Dysk",
                "Dyski", 0, null, null));

        // when
        BasketItem item = BasketItem.of(matched, 1, false);

        // then
        assertThat(item.getSku()).isEqualTo("5901234123457");
    }

    @Test
    void anItemFromTheInventoryCostsHalfwayBetweenTheLowestAndTheMedian() {
        // given — net 100 and 200: gross 123,00 and 246,00
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.hasAnyOffers()).thenReturn(true);
        when(matched.getTaxonomy()).thenReturn(mock(Taxonomy.class));
        when(matched.getLowestPrice()).thenReturn(Price.fromNet(100));
        when(matched.getMedianPrice()).thenReturn(Price.fromNet(200));

        // when
        BasketItem item = BasketItem.of(matched, 1, false);

        // then — client 2026-10-27: the estimate rather than the lowest price; the price stays the median
        assertThat(item.getUnitCost()).isEqualTo(184.50);
        assertThat(item.getUnitPrice()).isEqualTo(246.00);
    }

    @Test
    void legacyServicesCategoryStringAloneDoesNotMakeItemAService() {
        // given
        BasketItem item = new BasketItem("id", "name", "mfn", "Services", 100, 80, 1, null, 1, false);

        // when / then
        assertThat(item.isService()).isFalse();
    }

    @Test
    void shippingItemHasDeliveryCategoryLabel() {
        // given
        BasketItem shipping = BasketItem.shipping(10.0);

        // when / then
        assertThat(shipping.getCategory()).isEqualTo("Dostawa");
        assertThat(shipping.isService()).isTrue();
    }

    @Test
    void serviceItemWithoutCategoryIsComplete() {
        // given
        BasketItem item = new BasketItem("id", "Montaż PC", "MONTAZ-1", null, 100, 80, 1, null, 1, false);
        item.setService(true);

        // when / then
        assertThat(item.isComplete()).isTrue();
    }

    @Test
    void productItemWithoutCategoryIsNotComplete() {
        // given
        BasketItem item = new BasketItem("id", "name", "mfn", null, 100, 80, 1, null, 1, false);

        // when / then
        assertThat(item.isComplete()).isFalse();
    }

    private static MatchedInventory offeredProduct(Taxonomy taxonomy) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.hasAnyOffers()).thenReturn(true);
        when(matched.getTaxonomy()).thenReturn(taxonomy);
        when(matched.getLowestPrice()).thenReturn(Price.fromNet(100));
        when(matched.getMedianPrice()).thenReturn(Price.fromNet(200));
        return matched;
    }

    private AvailabilityAndPrice pricelistRow(String category) {
        return new AvailabilityAndPrice("pim", "ean", "mfn", "brand", "label", "name", category, 100, 1, 1, 100, false);
    }
}
