package pl.commercelink.baskets;

import org.junit.jupiter.api.Test;
import pl.commercelink.pricelist.AvailabilityAndPrice;

import static org.assertj.core.api.Assertions.assertThat;

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
    void serviceFlagSetExplicitlyMakesItemAService() {
        // given
        BasketItem item = BasketItem.of(pricelistRow("Usługi dodatkowe"), 1, "catalog-1", false);

        // when
        item.setService(true);

        // then
        assertThat(item.isService()).isTrue();
    }

    @Test
    void legacyServicesCategoryStringAloneDoesNotMakeItemAService() {
        // given
        BasketItem item = new BasketItem("id", "name", "mfn", "Services", 100, 80, 1, null, 1, false);

        // when / then
        assertThat(item.isService()).isFalse();
    }

    @Test
    void shippingItemIsService() {
        // when / then
        assertThat(BasketItem.shipping(10.0).isService()).isTrue();
    }

    @Test
    void shippingItemHasNoCategoryString() {
        // when / then
        assertThat(BasketItem.shipping(10.0).getCategory()).isNull();
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
