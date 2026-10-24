package pl.commercelink.web.activity;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.main;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.render;

class InactiveStorePageRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PATH = "/store/s1/client/order/a1b2c3d4e5";

    @Test
    void showsTheStoresBrandAndTheSellersEmailOnThePublicFrame() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setEmail("biuro@nova.pl");
        store.setBillingDetails(billing);
        Branding branding = new Branding();
        branding.setPrimaryColor("#0f766e");
        store.setBranding(branding);

        // when
        String html = render("store-inactive", PL, Map.of("publicPage", PublicPageFactory.of(store, PATH, PL)));

        // then
        assertThat(html).contains("<title>Sklep nieaktywny · Nova Tech</title>")
                .contains("<body class=\"cl-public\" style=\"--cl-brand:#0f766e;")
                .contains("<main id=\"clContent\" class=\"cl-page cl-public-main is-narrow\">")
                .contains("<section class=\"cl-card cl-public-message\">")
                .contains("<h1 class=\"cl-page-title\">Sklep jest nieaktywny</h1>")
                .contains("Strony tego sklepu są teraz niedostępne.")
                .contains("W sprawie zamówienia lub zwrotu napisz do sprzedawcy:")
                .contains("<a href=\"mailto:biuro@nova.pl\">biuro@nova.pl</a>")
                .doesNotContain("bezpośrednio ze sprzedawcą");
    }

    @Test
    void withoutAnEmailTellsTheCustomerToContactTheSellerDirectly() {
        // when
        String html = render("store-inactive", PL, Map.of("publicPage", PublicPageFactory.of(store(), PATH, PL)));

        // then
        assertThat(main(html)).contains("W sprawie zamówienia lub zwrotu skontaktuj się bezpośrednio ze sprzedawcą.")
                .doesNotContain("mailto:")
                .doesNotContain("cl-eyebrow");
    }

    @Test
    void rendersEnglishTexts() {
        // when
        String html = render("store-inactive", Locale.ENGLISH, Map.of("publicPage", PublicPageFactory.of(store(), PATH, Locale.ENGLISH)));

        // then
        assertThat(html).contains("<html lang=\"en\"")
                .contains("<title>Store inactive · Nova Tech</title>")
                .contains("This store is inactive")
                .contains("This store&#39;s pages are not available right now.")
                .contains("For questions about an order or a return, contact the seller directly.");
    }

    @Test
    void hasOneHeadingAndNoInlineScriptOrStyle() {
        // when
        String content = main(render("store-inactive", PL, Map.of("publicPage", PublicPageFactory.of(store(), PATH, PL))));

        // then
        assertThat(content.split("<h1", -1)).hasSize(2);
        assertThat(content).doesNotContain("<script").doesNotContain("<style").doesNotContain(" style=");
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        return store;
    }
}
