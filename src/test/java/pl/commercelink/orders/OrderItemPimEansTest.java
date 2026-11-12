package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.commercelink.externaloffers.ResolvedOffer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemPimEansTest {

    @Test
    void itemAddedByEanReportsThatEan() {
        // when / then
        assertThat(OrderItemPimEans.of(List.of(item("5901234123457")))).containsExactly("5901234123457");
    }

    @Test
    void onlyTheFirstCodeOfTheSkuIsReported() {
        // when / then
        assertThat(OrderItemPimEans.of(List.of(item("5901234123457:4006381333931"))))
                .containsExactly("5901234123457");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "RTX5070-12G", "5901234123458", "2222222222222", "#5901234123457"})
    void skuThatIsNotAnEanReportsNothing(String sku) {
        // when / then
        assertThat(OrderItemPimEans.of(List.of(item(sku)))).isEmpty();
    }

    @Test
    void serviceReportsNothing() {
        // given
        OrderItem service = item("5901234123457");
        service.setService(true);

        // when / then
        assertThat(OrderItemPimEans.of(List.of(service))).isEmpty();
    }

    @Test
    void linkedMarketplaceLineReportsItsEan() {
        // given
        OrderItem line = marketplaceLine("offer-1", ResolvedOffer.Source.LINK, "5901234123457:4006381333931", "5901234123457");

        // when / then
        assertThat(OrderItemPimEans.of(List.of(line))).containsExactly("5901234123457");
    }

    @Test
    void ownOfferLineReportsItsEan() {
        // given
        OrderItem line = marketplaceLine("pim-1", ResolvedOffer.Source.OWN_OFFER, "5901234123457", "5901234123457");

        // when / then
        assertThat(OrderItemPimEans.of(List.of(line))).containsExactly("5901234123457");
    }

    @Test
    void resolvedLineWithoutAnEanReportsNothingEvenWhenItsCodeLooksLikeOne() {
        // given
        OrderItem line = marketplaceLine("pim-1", ResolvedOffer.Source.OWN_OFFER, "4006381333931", null);

        // when / then
        assertThat(OrderItemPimEans.of(List.of(line))).isEmpty();
    }

    @Test
    void unlinkedLineWithAnOfferKeyReportsNothing() {
        // given
        OrderItem line = marketplaceLine("offer-1", null, "5901234123457", null);

        // when / then
        assertThat(OrderItemPimEans.of(List.of(line))).isEmpty();
    }

    @Test
    void unlinkedLineWithoutAnOfferKeyReportsNothing() {
        // given
        OrderItem line = marketplaceLine(null, null, "5901234123457", null);

        // when / then
        assertThat(OrderItemPimEans.of(List.of(line))).isEmpty();
    }

    @Test
    void legacyLineRecognisedByManufacturerCodeReportsNothing() {
        // given
        OrderItem line = marketplaceLine("offer-1", ResolvedOffer.Source.MFN, "4006381333931", null);

        // when / then
        assertThat(OrderItemPimEans.of(List.of(line))).isEmpty();
    }

    @Test
    void sameEanInTwoItemsIsReportedOnce() {
        // given
        List<OrderItem> items = List.of(item("5901234123457"), item("4006381333931"), item("5901234123457"));

        // when / then
        assertThat(OrderItemPimEans.of(items)).containsExactly("5901234123457", "4006381333931");
    }

    @Test
    void eanIsReportedInItsUnifiedSpelling() {
        // when / then
        assertThat(OrderItemPimEans.of(List.of(item(" 5901234123457 ")))).containsExactly("5901234123457");
    }

    @Test
    void noItemsReportNothing() {
        // when / then
        assertThat(OrderItemPimEans.of(List.of())).isEmpty();
    }

    private static OrderItem item(String sku) {
        return new OrderItem("order-1", "GPU", "MSI RTX 5070", 1, 100.0, sku, false);
    }

    private static OrderItem marketplaceLine(String offerKey, ResolvedOffer.Source source, String sku, String ean) {
        OrderItem item = item(sku);
        item.setExternalItemId("ext-1");
        item.setExternalOfferKey(offerKey);
        item.setExternalOfferSource(source);
        item.setEan(ean);
        return item;
    }
}
