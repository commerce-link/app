package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferRowMapperTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);
    private static final String ID = "8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b";

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static OfferRowMapper mapper(Store store) {
        return new OfferRowMapper(messages(), Locale.forLanguageTag("pl"), store, "https://app.example", NOW, "/dashboard/offers?q=CAD");
    }

    private static Basket offer() {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId(ID);
        basket.setType(BasketType.Offer);
        basket.setName("Stacje CAD");
        basket.setCreatedAt(LocalDateTime.of(2026, 10, 2, 9, 30));
        basket.setExpiresAt(LocalDateTime.of(2026, 10, 16, 23, 59));
        basket.setSource(new OrderSource("Jan Kowalski", OrderSourceType.CallCenter));
        ContactDetails contact = new ContactDetails();
        contact.setCompanyName("Biuro Lis s.c.");
        contact.setName("Anna");
        contact.setSurname("Lis");
        contact.setEmail("biuro@lis.pl");
        basket.setContactDetails(contact);
        basket.setBasketItems(new java.util.LinkedList<>(List.of(item(2, 1000.00), item(1, 460.00))));
        return basket;
    }

    private static BasketItem item(long qty, double unitPrice) {
        BasketItem item = new BasketItem();
        item.setQty(qty);
        item.setUnitPrice(unitPrice);
        item.setName("Item");
        return item;
    }

    private static Store storeWithoutCheckout() {
        Store store = mock(Store.class);
        when(store.getCheckoutConfiguration()).thenReturn(null);
        return store;
    }

    @Test
    void mapsAFullOfferRow() {
        // when
        OfferListPage.OfferRow row = mapper(storeWithoutCheckout()).offer(offer());

        // then
        assertThat(row.href()).isEqualTo("/dashboard/offer/" + ID);
        assertThat(row.name()).isEqualTo("Stacje CAD");
        assertThat(row.shortId()).isEqualTo("8f3a21c7");
        assertThat(row.itemsText()).isEqualTo("pozycje: 2");
        assertThat(row.client()).isEqualTo("Biuro Lis s.c.");
        assertThat(row.email()).isEqualTo("biuro@lis.pl");
        assertThat(row.created()).isEqualTo("02.10");
        assertThat(row.author()).isEqualTo("Jan Kowalski");
        assertThat(row.validityLabel()).isEqualTo("Ważna");
        assertThat(row.validityTone()).isEqualTo("is-ok");
        assertThat(row.validityNote()).isEqualTo("do 16.10 · dni: 13");
        assertThat(row.gross()).isEqualTo("2 460,00 PLN");
        assertThat(row.net()).isEqualTo("netto 2 000,00 PLN");
        assertThat(row.clientUrl()).startsWith("https://app.example/store/store-1/").endsWith(ID);
        assertThat(row.copyHref()).isEqualTo("/dashboard/offer/" + ID + "/copy");
        assertThat(row.copyWithContactHref()).isEqualTo("/dashboard/offer/" + ID + "/copy?withContact=true");
        assertThat(row.deleteHref()).isEqualTo("/dashboard/offer/" + ID + "/delete?returnTo=%2Fdashboard%2Foffers%3Fq%3DCAD");
        assertThat(row.deleteTitle()).isEqualTo("Usunąć ofertę „Stacje CAD”?");
    }

    @Test
    void expiringTomorrowSaysTomorrow() {
        // given
        Basket basket = offer();
        basket.setExpiresAt(LocalDateTime.of(2026, 10, 4, 23, 59));

        // when
        OfferListPage.OfferRow row = mapper(storeWithoutCheckout()).offer(basket);

        // then
        assertThat(row.validityLabel()).isEqualTo("Wygasa");
        assertThat(row.validityTone()).isEqualTo("is-warn");
        assertThat(row.validityNote()).isEqualTo("jutro, 04.10");
        assertThat(row.validityNoteWarn()).isTrue();
    }

    @Test
    void expiringTodaySaysTheHour() {
        // given
        Basket basket = offer();
        basket.setExpiresAt(LocalDateTime.of(2026, 10, 3, 23, 59));

        // when / then
        assertThat(mapper(storeWithoutCheckout()).offer(basket).validityNote()).isEqualTo("dziś, 23:59");
    }

    @Test
    void expiredAndWithoutDate() {
        // given
        Basket expired = offer();
        expired.setExpiresAt(LocalDateTime.of(2026, 9, 29, 23, 59));
        Basket open = offer();
        open.setExpiresAt(null);

        // when
        OfferListPage.OfferRow expiredRow = mapper(storeWithoutCheckout()).offer(expired);
        OfferListPage.OfferRow openRow = mapper(storeWithoutCheckout()).offer(open);

        // then
        assertThat(expiredRow.validityLabel()).isEqualTo("Wygasła");
        assertThat(expiredRow.validityTone()).isEqualTo("is-neutral");
        assertThat(expiredRow.validityNote()).isEqualTo("minęła 29.09");
        assertThat(openRow.validityLabel()).isEqualTo("Bez terminu");
        assertThat(openRow.validityNote()).isNull();
    }

    @Test
    void unnamedOfferUsesItsShortId() {
        // given
        Basket basket = offer();
        basket.setName("  ");

        // when / then
        assertThat(mapper(storeWithoutCheckout()).offer(basket).name()).isEqualTo("Oferta 8f3a21c7");
    }

    @Test
    void clientFallsBackFromCompanyToPersonToNone() {
        // given
        Basket person = offer();
        person.getContactDetails().setCompanyName(null);
        Basket nobody = offer();
        nobody.setContactDetails(null);
        Basket blank = offer();
        blank.setContactDetails(new ContactDetails());

        // when / then
        assertThat(mapper(storeWithoutCheckout()).offer(person).client()).isEqualTo("Anna Lis");
        assertThat(mapper(storeWithoutCheckout()).offer(nobody).client()).isNull();
        assertThat(mapper(storeWithoutCheckout()).offer(nobody).email()).isNull();
        assertThat(mapper(storeWithoutCheckout()).offer(blank).client()).isNull();
    }

    @Test
    void valueSkipsADeliveryOptionTheStoreCannotResolve() {
        // given — a delivery option chosen, but the store has no checkout configuration (getDeliveryPrice would NPE)
        Basket basket = offer();
        basket.setDeliveryOptionId("courier");

        // when
        OfferListPage.OfferRow row = mapper(storeWithoutCheckout()).offer(basket);

        // then
        assertThat(row.gross()).isEqualTo("2 460,00 PLN");
    }

    @Test
    void noAuthorLeavesTheLineOut() {
        // given
        Basket basket = offer();
        basket.setSource(null);

        // when / then
        assertThat(mapper(storeWithoutCheckout()).offer(basket).author()).isNull();
    }

    @Test
    void otherYearShowsTheYear() {
        // given
        Basket basket = offer();
        basket.setCreatedAt(LocalDateTime.of(2025, 12, 30, 10, 0));

        // when / then
        assertThat(mapper(storeWithoutCheckout()).offer(basket).created()).isEqualTo("30.12.2025");
    }

    @Test
    void templateRowCreatesAnOfferFromItself() {
        // given
        Basket template = offer();
        template.setType(BasketType.OfferTemplate);

        // when
        OfferListPage.TemplateRow row = mapper(storeWithoutCheckout()).template(template);

        // then
        assertThat(row.createOfferHref()).isEqualTo("/dashboard/offer/new?intent=template&sourceId=" + ID);
        assertThat(row.deleteTitle()).isEqualTo("Usunąć szablon „Stacje CAD”?");
    }

    @Test
    void storeBasketRowLeadsToThePreviewAndShowsTime() {
        // given
        Basket basket = offer();
        basket.setType(BasketType.Basket);
        basket.setContactDetails(null);

        // when
        OfferListPage.BasketRow row = mapper(storeWithoutCheckout()).basket(basket);

        // then
        assertThat(row.href()).isEqualTo("/dashboard/basket/view/" + ID);
        assertThat(row.created()).isEqualTo("02.10, 09:30");
        assertThat(row.client()).isEqualTo("gość, bez danych");
    }
}
