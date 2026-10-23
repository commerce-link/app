package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The "Zamów odbiór" page (shipping-pickup.html) rendered with Polish messages. */
class ShipmentPickupTemplateTest {

    private static final String BACK = "/dashboard/orders/7a3f2c1e-58d4-4c2b-9e61-0b7d3a9f4e12";
    private static final String KEY = "furgonetka|dpd|addr-1";

    private static ShipmentPickupPage page(List<ShipmentPickupPage.WindowOption> windows, String windowsError,
                                           String formError) {
        return page(windows, windowsError, formError, null);
    }

    private static ShipmentPickupPage page(List<ShipmentPickupPage.WindowOption> windows, String windowsError,
                                           String formError, String refusal) {
        return new ShipmentPickupPage(
                List.of(new ShipmentPickupPage.GroupOption("furgonetka|dhl|addr-1", "dhl · Furgonetka · Magazyn · paczek: 1", false),
                        new ShipmentPickupPage.GroupOption(KEY, "dpd · Furgonetka · Magazyn · paczek: 2", true)),
                KEY,
                List.of(new ShipmentPickupPage.PackageRow("1", "7a3f2c1e · TRK-1", "to zamówienie", null),
                        new ShipmentPickupPage.PackageRow("2", "91c4e0b2 · TRK-2", null, refusal)),
                "Magazyn, Magazynowa 1, 00-001 Warszawa", windows, windowsError, formError, BACK);
    }

    private static String render(ShipmentPickupPage page) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("pickupPage", page);
        String html = SettingsTemplateRenderer.render("shipping-pickup", variables);
        int start = html.indexOf("<section class=\"cl-page\"");
        return html.substring(start, html.indexOf("</section>", start) + "</section>".length()).replaceAll("\\s+", " ");
    }

    @Test
    void theChosenGroupIsShownWithItsPackagesAndWindows() {
        // given
        ShipmentPickupPage page = page(List.of(
                new ShipmentPickupPage.WindowOption("2026-10-08|09:00|17:00|h-1", "czw. 8 paź, 9:00–17:00"),
                new ShipmentPickupPage.WindowOption("2026-10-09|09:00|12:00|", "pt. 9 paź, 9:00–12:00")), null, null);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Zamów odbiór</h1>")
                .contains("href=\"" + BACK + "\"")
                .contains("Kurier odbierze paczki spod adresu nadania.")
                .contains("action=\"/dashboard/shipping/pickups/new\"").contains("data-cl-submit-on-change")
                .contains("<option value=\"furgonetka|dpd|addr-1\" selected=\"selected\">dpd · Furgonetka · Magazyn · paczek: 2</option>")
                .contains("<noscript>").contains(">Zmień</button>")
                .contains("action=\"/dashboard/shipping/pickups\"")
                .contains("<input type=\"hidden\" name=\"group\" value=\"furgonetka|dpd|addr-1\">")
                .contains("<input type=\"hidden\" name=\"back\" value=\"" + BACK + "\">")
                .contains("<input type=\"checkbox\" name=\"externalIds\" value=\"1\" checked=\"checked\">")
                .contains("<span>7a3f2c1e · TRK-1</span> <span class=\"cl-optional\">to zamówienie</span>")
                .contains("Magazyn, Magazynowa 1, 00-001 Warszawa")
                .contains("class=\"cl-choice-group is-row\" aria-label=\"Dzień i godziny odbioru\"")
                .contains("<input type=\"radio\" name=\"window\" value=\"2026-10-08|09:00|17:00|h-1\" checked=\"checked\">")
                .contains("<input type=\"radio\" name=\"window\" value=\"2026-10-09|09:00|12:00|\">")
                .contains("czw. 8 paź, 9:00–17:00")
                .contains("Okna odbioru podał przewoźnik.")
                .contains("<button type=\"submit\" class=\"cl-button is-primary\">Zamów odbiór</button>")
                .doesNotContain("cl-alert")
                .doesNotContain("style=").doesNotContain("onclick").doesNotContain("onchange");
    }

    @Test
    void withoutWindowsThePageSaysSoAndOffersNoButton() {
        // when
        String html = render(page(List.of(), null, null));

        // then
        assertThat(html).contains("id=\"pickup-no-windows\"")
                .contains("Przewoźnik nie podał terminów odbioru — spróbuj później albo nadaj paczki w punkcie.")
                .doesNotContain("name=\"window\"")
                .doesNotContain("cl-button is-primary");
    }

    @Test
    void aProviderErrorReplacesTheWindows() {
        // when
        String html = render(page(List.of(), "Brak usługi odbioru", null));

        // then
        assertThat(html).contains("id=\"pickup-windows-error\"").contains("Brak usługi odbioru")
                .doesNotContain("id=\"pickup-no-windows\"")
                .doesNotContain("cl-button is-primary");
    }

    @Test
    void aFormErrorIsShownAboveTheForms() {
        // when
        String html = render(page(List.of(), null, "Zaznacz co najmniej jedną paczkę."));

        // then
        assertThat(html).contains("<div class=\"cl-alert is-bad\" id=\"pickup-form-error\" role=\"alert\">")
                .contains("Zaznacz co najmniej jedną paczkę.");
    }

    @Test
    void withNothingWaitingOnlyTheEmptyStateIsShown() {
        // given
        ShipmentPickupPage empty = new ShipmentPickupPage(List.of(), null, List.of(), null, List.of(), null, null,
                "/dashboard/orders");

        // when
        String html = render(empty);

        // then
        assertThat(html).contains("<p class=\"cl-list-empty\">Żadna paczka nie czeka na odbiór.</p>")
                .doesNotContain("<form");
    }

    @Test
    void aPackageRowIsOnlyItsCheckboxWithNoActionBesideIt() {
        // when
        String html = render(page(List.of(), null, null));

        // then: every package goes through CommerceLink (client decision 2026-10-07), nothing marks it handed over
        assertThat(html).contains("<ul class=\"cl-check-list\">")
                .doesNotContain("has-actions").doesNotContain("handed-over").doesNotContain("formaction")
                .doesNotContain("Przekazana poza CommerceLink");
    }

    @Test
    void aPackageTheCarrierRefusesIsUntickedDisabledAndExplained() {
        // given
        ShipmentPickupPage page = page(List.of(
                new ShipmentPickupPage.WindowOption("2026-10-08|09:00|17:00|h-1", "czw. 8 paź, 9:00–17:00")), null, null,
                "Przewoźnik nie poda terminu odbioru tej paczki: Przesyłka została już zamówiona.");

        // when
        String html = render(page);

        // then
        assertThat(html).contains("<input type=\"checkbox\" name=\"externalIds\" value=\"2\" disabled=\"disabled\">")
                .contains("<p class=\"cl-check-note is-warn\">Przewoźnik nie poda terminu odbioru tej paczki: "
                        + "Przesyłka została już zamówiona.</p>")
                .contains("<button type=\"submit\" class=\"cl-button is-primary\">Zamów odbiór</button>");
    }
}
