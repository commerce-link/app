package pl.commercelink.web.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOfferLineTest {

    @Test
    @DisplayName("a variant group collapses into one line whose options are priced relative to the first item by default")
    void groupCollapsesIntoOneLinePricedRelativeToDefaultItem() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(item("MFN-CPU", "CPU", 500.0, null), item("MFN-SSD-1", "SSD", 449.0, "Dysk"), item("MFN-SSD-2", "SSD", 829.0, "Dysk")));

        // when
        List<ClientOfferLine> lines = ClientOfferLine.from(basket);

        // then
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).isGroup()).isFalse();
        assertThat(lines.get(0).getLabel()).isEqualTo("CPU");
        ClientOfferLine group = lines.get(1);
        assertThat(group.isGroup()).isTrue();
        assertThat(group.getLabel()).isEqualTo("Dysk");
        assertThat(group.item().getMfn()).isEqualTo("MFN-SSD-1");
        assertThat(group.options()).extracting(ClientOfferLine.Option::priceDelta).containsExactly(0.0, 380.0);
        assertThat(group.options()).extracting(ClientOfferLine.Option::selected).containsExactly(true, false);
    }

    @Test
    @DisplayName("after the client picks a variant the deltas are recomputed relative to the chosen item")
    void deltasFollowTheChosenVariant() {
        // given
        Basket basket = new Basket();
        BasketItem ssd1 = item("MFN-SSD-1", "SSD", 449.0, "Dysk");
        BasketItem ssd2 = item("MFN-SSD-2", "SSD", 829.0, "Dysk");
        BasketItem ssd3 = item("MFN-SSD-3", "SSD", 1599.0, "Dysk");
        basket.setBasketItems(List.of(ssd1, ssd2, ssd3));
        basket.selectVariant("Dysk", ssd2.getPosition());

        // when
        ClientOfferLine group = ClientOfferLine.from(basket).get(0);

        // then
        assertThat(group.item().getMfn()).isEqualTo("MFN-SSD-2");
        assertThat(group.options()).extracting(ClientOfferLine.Option::priceDelta).containsExactly(-380.0, 0.0, 770.0);
        assertThat(group.options()).extracting(ClientOfferLine.Option::isIncluded).containsExactly(false, true, false);
        assertThat(group.options().get(0).absPriceDelta()).isEqualTo(380.0);
    }

    @Test
    @DisplayName("a group with a single item renders as a plain line")
    void singleItemGroupRendersAsPlainLine() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(item("MFN-SSD-1", "SSD", 449.0, "Dysk")));

        // when
        ClientOfferLine line = ClientOfferLine.from(basket).get(0);

        // then
        assertThat(line.isGroup()).isFalse();
        assertThat(line.getLabel()).isEqualTo("SSD");
    }

    private static BasketItem item(String mfn, String category, double price, String groupId) {
        BasketItem item = new BasketItem("pim-" + mfn, "Product " + mfn, mfn, category, price, 0, 1, null, 3, false);
        item.setVariantGroupId(groupId);
        return item;
    }
}
