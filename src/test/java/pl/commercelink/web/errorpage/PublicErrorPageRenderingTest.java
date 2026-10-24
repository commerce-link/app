package pl.commercelink.web.errorpage;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.main;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.render;

class PublicErrorPageRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PATH = "/store/s1/client/offer/o1/nie-ma";

    @Test
    void notFoundShowsTheCodeTheMessageAndTheStoresEmail() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setEmail("biuro@nova.pl");
        store.setBillingDetails(billing);

        // when
        String html = render(PublicErrorViewResolver.VIEW, PL,
                Map.of("publicPage", PublicPageFactory.of(store, PATH, PL), "errorStatus", 404, "notFound", true));

        // then
        assertThat(html).contains("<title>Nie ma takiej strony · Nova Tech</title>")
                .contains("<p class=\"cl-eyebrow\">Błąd 404</p>")
                .contains("<h1 class=\"cl-page-title\">Nie ma takiej strony</h1>")
                .contains("Link jest nieprawidłowy albo niepełny.")
                .contains("Jeśli potrzebujesz pomocy, napisz do nas:")
                .contains("<a href=\"mailto:biuro@nova.pl\">biuro@nova.pl</a>");
    }

    @Test
    void anyOtherErrorAsksToTryAgain() {
        // when
        String html = render(PublicErrorViewResolver.VIEW, PL,
                Map.of("publicPage", PublicPageFactory.of(store(), PATH, PL), "errorStatus", 500, "notFound", false));

        // then
        assertThat(html).contains("<title>Coś poszło nie tak · Nova Tech</title>")
                .contains("<p class=\"cl-eyebrow\">Błąd 500</p>")
                .contains("<h1 class=\"cl-page-title\">Nie udało się wyświetlić tej strony</h1>")
                .contains("Spróbuj ponownie za chwilę.")
                .doesNotContain("mailto:");
    }

    @Test
    void unknownStoreInEnglishHasAPlainTitle() {
        // when
        String html = render(PublicErrorViewResolver.VIEW, Locale.ENGLISH,
                Map.of("publicPage", PublicPageFactory.unknownStore(PATH, Locale.ENGLISH), "errorStatus", 404, "notFound", true));

        // then
        assertThat(html).contains("<title>Page not found</title>")
                .contains("<p class=\"cl-eyebrow\">Error 404</p>")
                .contains("This page does not exist")
                .doesNotContain("cl-public-foot");
    }

    @Test
    void hasOneHeadingAndNoInlineScriptOrStyle() {
        // when
        String content = main(render(PublicErrorViewResolver.VIEW, PL,
                Map.of("publicPage", PublicPageFactory.of(store(), PATH, PL), "errorStatus", 404, "notFound", true)));

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
