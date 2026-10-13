package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Settings › Shipping lists the store's shipping integrations; Wysyłam z Allegro is a row next to the courier account. */
class StoreShippingIntegrationsTemplateTest {

    private static final IntegrationStatus FURGONETKA = new IntegrationStatus("furgonetka", "Furgonetka", true, true);

    @Test
    void bothIntegrationsAreListedWithTheSuggestionRule() {
        // when
        String html = render(FURGONETKA, new AllegroShippingSettings.AllegroShippingSummary(true, true, true,
                AllegroShippingLabelFormat.ZPL));

        // then
        assertThat(html).contains("id=\"shipping-integrations\"").contains("Shipping integrations");
        assertThat(html).contains("Furgonetka").contains(">Default<");
        assertThat(html).contains("Wysyłam z Allegro").contains("Allegro orders").contains("Label ZPL");
        assertThat(html).contains("we suggest Wysyłam z Allegro for Allegro orders and Furgonetka for the rest");
        assertThat(html).contains("href=\"/dashboard/store/shipping/allegro\"");
        assertThat(html).contains("Furgonetka carriers");
        assertThat(html).doesNotContain("id=\"shipping-account\"");
    }

    @Test
    void anInstalledButSwitchedOffAllegroOffersToEnableIt() {
        // when
        String html = render(FURGONETKA, new AllegroShippingSettings.AllegroShippingSummary(true, false, true,
                AllegroShippingLabelFormat.PDF_A6));

        // then
        assertThat(html).contains("aria-label=\"Enable: Wysyłam z Allegro\"");
        assertThat(html).doesNotContain("we suggest Wysyłam z Allegro");
        assertThat(html).doesNotContain("Disconnect: Wysyłam z Allegro");
    }

    @Test
    void withoutTheAllegroMarketplaceTheRowSaysWhatToDoFirst() {
        // when
        String html = render(FURGONETKA, new AllegroShippingSettings.AllegroShippingSummary(true, false, false,
                AllegroShippingLabelFormat.PDF_A6));

        // then
        assertThat(html).contains("Connect Allegro in Marketplaces first.");
    }

    @Test
    void anAdapterThatIsNotInstalledShowsNoAllegroRow() {
        // when
        String html = render(FURGONETKA, new AllegroShippingSettings.AllegroShippingSummary(false, false, true,
                AllegroShippingLabelFormat.PDF_A6));

        // then
        assertThat(html).doesNotContain("Wysyłam z Allegro");
    }

    @Test
    void aStoreWithOnlyWysylamZAllegroHasNoDefaultRowButCanConnectACourier() {
        // when
        String html = render(IntegrationStatus.none(), new AllegroShippingSettings.AllegroShippingSummary(true, true, true,
                AllegroShippingLabelFormat.PDF_A6));

        // then
        assertThat(html).doesNotContain(">Default<");
        assertThat(html).contains("Wysyłam z Allegro");
        assertThat(html).contains("href=\"/dashboard/store/shipping/account\"");
    }

    @Test
    void theUnverifiedWebhookWarningStaysOnTheCourierRow() {
        // when
        Context context = context(FURGONETKA, new AllegroShippingSettings.AllegroShippingSummary(false, false, false,
                AllegroShippingLabelFormat.PDF_A6));
        context.setVariable("webhookTokenMissing", true);
        String html = EnglishFragmentTemplateEngine.create().process("store-shipping", context);

        // then
        assertThat(html).contains("cl-list-desc is-warn");
    }

    private static String render(IntegrationStatus account, AllegroShippingSettings.AllegroShippingSummary allegro) {
        return EnglishFragmentTemplateEngine.create().process("store-shipping", context(account, allegro));
    }

    private static Context context(IntegrationStatus account, AllegroShippingSettings.AllegroShippingSummary allegro) {
        Context context = new Context();
        context.setVariable("account", account);
        context.setVariable("webhookTokenMissing", false);
        context.setVariable("accountHref", "/dashboard/store/shipping/account");
        context.setVariable("disconnectHref", "/dashboard/store/shipping/account/disconnect");
        context.setVariable("allegroShipping", allegro);
        context.setVariable("allegroHref", "/dashboard/store/shipping/allegro");
        context.setVariable("allegroDisconnectHref", "/dashboard/store/shipping/allegro/disconnect");
        context.setVariable("carriers", List.of("DPD", "InPost"));
        context.setVariable("carriersHref", "/dashboard/store/shipping/carriers");
        context.setVariable("addresses", List.of());
        context.setVariable("newAddressHref", "/dashboard/store/shipping/addresses/new");
        context.setVariable("sender", null);
        context.setVariable("senderHref", "/dashboard/store/shipping/sender");
        context.setVariable("templates", List.of());
        context.setVariable("newTemplateHref", "/dashboard/store/shipping/templates/new");
        context.setVariable("savedMessage", null);
        return context;
    }
}
