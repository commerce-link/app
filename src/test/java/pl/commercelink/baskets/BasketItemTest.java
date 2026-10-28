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

    private AvailabilityAndPrice pricelistRow(String category) {
        return new AvailabilityAndPrice("pim", "ean", "mfn", "brand", "label", "name", category, 100, 1, 1, 100, false);
    }
}
