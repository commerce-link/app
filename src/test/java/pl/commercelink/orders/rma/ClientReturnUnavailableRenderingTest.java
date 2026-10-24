package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.main;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.render;

class ClientReturnUnavailableRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PATH = "/store/s1/client/rma/r1";

    @Test
    void showsTheMessageAndTheStoresEmailWithoutAReturnNumber() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setEmail("zwroty@nova.pl");
        store.setBillingDetails(billing);

        // when
        String html = render(RMAClientController.UNAVAILABLE, PL, Map.of("publicPage", PublicPageFactory.of(store, PATH, PL)));

        // then
        assertThat(html).contains("<title>Zwrot niedostępny · Nova Tech</title>")
                .contains("<h1 class=\"cl-page-title\">Formularz zwrotu nie jest dostępny</h1>")
                .contains("Link jest nieprawidłowy albo zwrot nie czeka już na nadanie paczki.")
                .contains("Jeśli masz pytanie o zwrot, napisz do nas:")
                .contains("<a href=\"mailto:zwroty@nova.pl\">zwroty@nova.pl</a>");
        assertThat(main(html)).doesNotContain("cl-eyebrow").doesNotContain("r1");
    }

    @Test
    void unknownStoreHasAPlainTitleAndNoFooter() {
        // when
        String html = render(RMAClientController.UNAVAILABLE, PL, Map.of("publicPage", PublicPageFactory.unknownStore(PATH, PL)));

        // then
        assertThat(html).contains("<title>Zwrot niedostępny</title>")
                .doesNotContain("cl-public-foot")
                .doesNotContain("mailto:");
    }

    @Test
    void rendersEnglishTexts() {
        // when
        String html = render(RMAClientController.UNAVAILABLE, Locale.ENGLISH,
                Map.of("publicPage", PublicPageFactory.of(store(), PATH, Locale.ENGLISH)));

        // then
        assertThat(html).contains("<title>Return unavailable · Nova Tech</title>")
                .contains("The return form is not available")
                .contains("The link is not valid or the return is no longer waiting for a parcel to be sent.");
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        return store;
    }
}
