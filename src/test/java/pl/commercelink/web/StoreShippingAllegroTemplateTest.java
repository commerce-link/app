package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The Wysyłam z Allegro page: a status card that says whether it can work, and the label form only when it can. */
class StoreShippingAllegroTemplateTest {

    @Test
    void aWorkingConnectionShowsTheFormWithTheSavedFormat() {
        // when
        String html = render(AllegroShippingStatus.ENABLED, AllegroShippingLabelFormat.ZPL, null);

        // then
        assertThat(html).contains("Allegro orders are shipped through Wysyłam z Allegro");
        assertThat(html).contains("id=\"allegro-shipping-form\"");
        assertThat(html).containsPattern("value=\"ZPL\"[^>]*checked|checked[^>]*value=\"ZPL\"");
        assertThat(html).contains("Save changes");
        assertThat(html).doesNotContain("Package template");
    }

    @Test
    void aReadyConnectionOffersToEnable() {
        // when
        String html = render(AllegroShippingStatus.READY, AllegroShippingLabelFormat.PDF_A6, null);

        // then
        assertThat(html).contains("You can enable Wysyłam z Allegro").contains("Enable Wysyłam z Allegro");
    }

    @Test
    void aMissingConsentShowsTheStepsAndNoForm() {
        // when
        String html = render(AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT, AllegroShippingLabelFormat.PDF_A6, null);

        // then
        assertThat(html).contains("the shipments consent is missing");
        assertThat(html).contains("href=\"/dashboard/store/marketplaces/Allegro/authorize\"");
        assertThat(html).contains("class=\"cl-steps\"");
        assertThat(html).doesNotContain("id=\"allegro-shipping-form\"");
    }

    @Test
    void withoutTheMarketplaceThePageLeadsToMarketplaces() {
        // when
        String html = render(AllegroShippingStatus.MARKETPLACE_NOT_CONNECTED, AllegroShippingLabelFormat.PDF_A6, null);

        // then
        assertThat(html).contains("Allegro is not connected").contains("href=\"/dashboard/store/marketplaces\"");
        assertThat(html).doesNotContain("id=\"allegro-shipping-form\"");
    }

    @Test
    void aFailedCheckCanBeRepeated() {
        // when
        String html = render(AllegroShippingStatus.CHECK_FAILED, AllegroShippingLabelFormat.PDF_A6, null);

        // then
        assertThat(html).contains("could not be checked").contains("Try again");
        assertThat(html).doesNotContain("id=\"allegro-shipping-form\"");
    }

    @Test
    void aRefusedSaveIsSaidAboveTheCard() {
        // when
        String html = render(AllegroShippingStatus.MISSING_SHIPMENTS_CONSENT, AllegroShippingLabelFormat.PDF_A6,
                "Wysyłam z Allegro cannot be enabled now; check the connection status above.");

        // then
        assertThat(html).contains("cl-alert is-bad").contains("cannot be enabled now");
    }

    private static String render(AllegroShippingStatus status, AllegroShippingLabelFormat format, String error) {
        Context context = new Context();
        context.setVariable("status", status);
        context.setVariable("labelFormat", format);
        context.setVariable("labelFormats", List.of(AllegroShippingLabelFormat.values()));
        context.setVariable("errorMessage", error);
        context.setVariable("formAction", "/dashboard/store/shipping/allegro");
        context.setVariable("pageHref", "/dashboard/store/shipping/allegro");
        context.setVariable("reconnectHref", "/dashboard/store/marketplaces/Allegro/authorize");
        context.setVariable("marketplacesHref", "/dashboard/store/marketplaces");
        context.setVariable("shippingHref", "/dashboard/store/shipping");
        context.setVariable("backLabel", "Shipping");
        context.setVariable("pageTitle", "Wysyłam z Allegro");
        return EnglishFragmentTemplateEngine.create().process("store-shipping-allegro", context);
    }
}
