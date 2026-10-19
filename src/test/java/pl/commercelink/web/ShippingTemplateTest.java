package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.AllegroShippingView;
import pl.commercelink.shipping.ParcelForm;
import pl.commercelink.shipping.ShippingIntegrationChoiceView;
import pl.commercelink.shipping.ShippingIntegrationOption;
import pl.commercelink.shipping.api.DeliveryPoint;
import pl.commercelink.shipping.api.DeliveryType;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.shipping.ShippingPageView;
import pl.commercelink.shipping.api.ShippingEstimate;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The courier booking page (shipping.html) as orders, RMA and the warehouse render it, with Polish messages. */
class ShippingTemplateTest {

    static final String ORDER_ID = "3e373abc-1111-2222-3333-444455556666";
    static final Path TEMPLATE = Path.of("src/main/resources/templates/shipping.html");

    static ShippingDetails recipient(String name, String street) {
        ShippingDetails details = new ShippingDetails();
        details.setName(name);
        details.setSurname("Kowalski");
        details.setCompanyName("Serwis Sp. z o.o.");
        details.setStreetAndNumber(street);
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setEmail("jan@example.pl");
        details.setPhone("500600700");
        return details;
    }

    static PackageTemplate template(String id, String name, boolean isDefault) {
        PackageTemplate template = new PackageTemplate(name, List.of());
        template.setId(id);
        template.setDefault(isDefault);
        return template;
    }

    static Map<String, Object> model(ShippingForm form, List<ShippingDetails> recipients, ShippingPageView view) {
        if (form.getShippingDetails() == null && !recipients.isEmpty()) {
            form.setShippingDetails(recipients.get(0));
        }
        ShippingDetails pickup = recipient("Magazyn", "Magazynowa 1");
        pickup.setId("pickup-1");
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("shippingForm", form);
        variables.put("shippingEntityId", form.getShippingEntityId());
        variables.put("shippingDetailsList", recipients);
        variables.put("deliveryPointCode", null);
        variables.put("pickUpAddresses", List.of(pickup));
        variables.put("packageTemplates", List.of(template("t-small", "Mała paczka", false), template("t-pc", "Komputer", true)));
        variables.put("shippingPage", view);
        return variables;
    }

    static ShippingPageView orderView() {
        return new ShippingPageView("/dashboard/orders/" + ORDER_ID, "order.page.title", "3e373abc",
                "shipping.lead.order", "3e373abc");
    }

    static String page(String html) {
        int start = html.indexOf("<section class=\"cl-page\"");
        int createForm = Math.max(html.lastIndexOf("shipping-create-form"), html.lastIndexOf("allegro-create-form"));
        return html.substring(start, html.indexOf("</section>", createForm) + "</section>".length());
    }

    static String render(Map<String, Object> variables) {
        return page(SettingsTemplateRenderer.render("shipping", variables)).replaceAll("\\s+", " ");
    }

    static ShippingForm pricedOrderForm() {
        ShippingForm form = new ShippingForm(ORDER_ID, "orders");
        form.setPickUpAddressId("pickup-1");
        form.setPackageTemplateId("t-small");
        form.setCashOnDelivery(true);
        form.setCashOnDeliveryAmount(149.5);
        List<ParcelForm> parcels = new ArrayList<>();
        parcels.add(new ParcelForm(40, 60, 30, 8, 1797, "Komputer", "package"));
        parcels.add(ParcelForm.empty());
        form.setParcels(parcels);
        form.setServiceId("dpd");
        return form;
    }

    @Test
    void beforeTheParcelsAreLoadedOnlyTheFirstStepCanBeSent() {
        // given
        Map<String, Object> variables = model(new ShippingForm(ORDER_ID, "orders"),
                List.of(recipient("Jan", "Prosta 5")), orderView());
        variables.put("preferredShippingWarning", "2026-10-02");

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Nadaj przesyłkę</h1>")
                .contains("href=\"/dashboard/orders/" + ORDER_ID + "\"").contains("Zamówienie 3e373abc")
                .contains("Przesyłka do klienta z zamówienia 3e373abc.")
                .contains("class=\"cl-alert is-warn\"").contains("Klient poprosił o wysyłkę 2026-10-02.")
                .contains("action=\"/dashboard/orders/" + ORDER_ID + "/shipping/template\"")
                .contains("<legend class=\"cl-fieldset-title\">Adres dostawy</legend>")
                .contains("data-cl-recipient-view=\"street\">Prosta 5</div>")
                .contains("name=\"shippingDetails.streetAndNumber\" value=\"Prosta 5\" data-cl-recipient-field=\"street\"")
                .contains("<option value=\"pickup-1\">Magazynowa 1 Warszawa</option>")
                .contains("<option value=\"t-pc\" selected=\"selected\">Komputer</option>")
                .contains("class=\"cl-button is-primary\">Wczytaj paczki</button>")
                .contains("Wybierz szablon paczek i kliknij „Wczytaj paczki”.")
                .contains("Wyceń przesyłkę, żeby zobaczyć oferty przewoźników.")
                .doesNotContain("id=\"shipping-parcels\"").doesNotContain("Wyceń przesyłkę</button>")
                .doesNotContain("Utwórz przesyłkę</button>").doesNotContain("Kuriera po paczkę zamówisz").doesNotContain("data-cl-recipient-select")
                .doesNotContain("??");
    }

    @Test
    void afterPricingTheParcelsOptionsAndOffersAreEditableAndCarriedToTheBooking() {
        // given
        Map<String, Object> variables = model(pricedOrderForm(), List.of(recipient("Jan", "Prosta 5")), orderView());
        variables.put("servicePrices", List.of(
                new ShippingEstimate("dpd", "DPD", true, new BigDecimal("23.50"), new BigDecimal("19.11"), List.of()),
                new ShippingEstimate("ups", "UPS", false, null, null, List.of("Za ciężka paczka"))));

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("id=\"shipping-parcels\"")
                .contains("<th scope=\"row\" class=\"cl-table-key\">Paczka 1</th>")
                .contains("name=\"parcels[0].depth\" value=\"60\"").contains("name=\"parcels[0].value\" value=\"1797\"")
                .contains("aria-label=\"Paczka 1 · Długość (cm)\"").contains("data-label=\"Zawartość\"")
                .contains("<input type=\"hidden\" name=\"parcels[0].type\" value=\"package\">")
                .contains("id=\"cashOnDelivery\" name=\"cashOnDelivery\" value=\"true\" data-cl-reveal=\"shipping-cod\" aria-controls=\"shipping-cod\" checked=\"checked\"")
                .contains("<div class=\"cl-reveal\" id=\"shipping-cod\">")
                .contains("class=\"cl-input is-price\"").contains("name=\"cashOnDeliveryAmount\" value=\"149.5\"")
                .contains("class=\"cl-button\">Wyceń przesyłkę</button>")
                .contains("value=\"dpd\" checked=\"checked\"").contains("23,5 PLN brutto")
                .contains("value=\"ups\" disabled=\"disabled\"").contains("Niedostępna")
                .contains("<span class=\"cl-choice-description is-warn\">Za ciężka paczka</span>")
                .contains("class=\"cl-button is-primary\">Utwórz przesyłkę</button>")
                .contains("<p class=\"cl-help\">Kuriera po paczkę zamówisz potem przyciskiem „Zamów odbiór” na liście zamówień, razem z innymi paczkami tego przewoźnika.</p>")
                .contains("action=\"/dashboard/orders/" + ORDER_ID + "/shipping/create\"")
                .contains("<input type=\"hidden\" name=\"parcels[1].description\" value=\"\">")
                .contains("<input type=\"hidden\" name=\"pickUpAddressId\" value=\"pickup-1\">")
                .doesNotContain("??");
        assertThat(OrderDetailsTemplateTest.occurrences(html, "name=\"cashOnDelivery\"")).isEqualTo(2);
        assertThat(OrderDetailsTemplateTest.occurrences(html, "name=\"shippingDetails.phone\" value=\"500600700\"")).isEqualTo(3);
    }

    @Test
    void anRmaWithSeveralServiceCentresLetsTheOperatorChooseTheRecipient() {
        // given
        ShippingForm form = new ShippingForm("rma-7", "rma");
        form.setOrderItemIds(List.of("item-1", "item-2"));
        ShippingPageView view = new ShippingPageView("/dashboard/rma/rma-7", "rma.details", null,
                "shipping.lead.rma.center", "rma-7");

        // when
        String html = render(model(form, List.of(recipient("Serwis A", "Długa 1"), recipient("Serwis B", "Krótka 2")), view));

        // then
        assertThat(html).contains("href=\"/dashboard/rma/rma-7\"").contains("Szczegóły RMA")
                .contains("Wysyłka produktów z reklamacji rma-7 do dystrybutora.")
                .contains("<select class=\"cl-select\" id=\"shipping-recipient\" data-cl-recipient-select>")
                .contains("data-street=\"Długa 1\" data-postal=\"00-001\" data-city=\"Warszawa\" data-country=\"PL\" data-email=\"jan@example.pl\" data-phone=\"500600700\" selected=\"selected\">")
                .contains("data-street=\"Krótka 2\"")
                .contains("Serwis B Kowalski, Krótka 2, Warszawa</option>")
                .contains("action=\"/dashboard/rma/rma-7/shipping/template\"")
                .contains("<input type=\"hidden\" name=\"orderItemIds\" value=\"item-2\">")
                .contains("<input type=\"hidden\" name=\"toClient\" value=\"false\">")
                .doesNotContain("??");
    }

    @Test
    void warehouseItemsHaveNoRecordIdAndGoBackToTheWarehouse() {
        // given
        ShippingForm form = new ShippingForm(null, "warehouse");
        form.setOrderItemIds(List.of("w-1", "w-2", "w-3"));
        ShippingPageView view = new ShippingPageView("/dashboard/warehouse", "nav.warehouse", null,
                "shipping.lead.warehouse", 3);

        // when
        String html = render(model(form, List.of(recipient("Serwis", "Długa 1")), view));

        // then
        assertThat(html).contains("href=\"/dashboard/warehouse\"")
                .contains("Wysyłka przedmiotów z magazynu do dystrybutora. Przedmioty: 3")
                .contains("action=\"/dashboard/warehouse/shipping/template\"")
                .doesNotContain("name=\"shippingEntityId\"").doesNotContain("??");
    }

    @Test
    void withoutACourierAccountTheReasonIsThePagesOwnAlert() {
        // given
        Map<String, Object> variables = model(pricedOrderForm(), List.of(recipient("Jan", "Prosta 5")), orderView());
        variables.put("shippingUnavailable", "Sklep nie ma podłączonego przewoźnika — dane nadania wpisz w przesyłce.");

        // when
        String html = render(variables);

        // then: a warning alert of the page, not the layout's old banner; no offers to book
        assertThat(html).contains("<div class=\"cl-alert is-warn\" id=\"shipping-unavailable\" role=\"alert\">")
                .contains("Sklep nie ma podłączonego przewoźnika — dane nadania wpisz w przesyłce.")
                .doesNotContain("id=\"shipping-create-submit\"");
    }

    @Test
    void theCourierButtonIsDisabledOnceItsFormIsSent() throws IOException {
        // given
        String js = Files.readString(Path.of("src/main/resources/static/js/shipping-booking.js"), StandardCharsets.UTF_8);
        Map<String, Object> variables = model(pricedOrderForm(), List.of(recipient("Jan", "Prosta 5")), orderView());
        variables.put("servicePrices", List.of(
                new ShippingEstimate("dpd", "DPD", true, new BigDecimal("23.50"), new BigDecimal("19.11"), List.of())));

        // when
        String html = render(variables);

        // then: one click books one label; the button has no name, so disabling it drops nothing from the post
        int button = html.indexOf("id=\"shipping-create-submit\"");
        assertThat(button).isPositive();
        assertThat(html.substring(html.lastIndexOf("<button", button), html.indexOf(">", button))).doesNotContain("name=");
        assertThat(js).contains("event.submitter && event.submitter.id === 'shipping-create-submit'")
                .contains("event.submitter.disabled = true").contains("addEventListener('pageshow'")
                .contains("event.persisted");
    }

    @Test
    void theTemplateHasNoInlineBehaviourNoBulmaLookAndNoDuplicateIds() throws IOException {
        // given
        String source = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        Map<String, Object> variables = model(pricedOrderForm(), List.of(recipient("Jan", "Prosta 5")), orderView());
        variables.put("servicePrices", List.of(
                new ShippingEstimate("dpd", "DPD", true, BigDecimal.TEN, BigDecimal.ONE, List.of())));

        // when
        String html = render(variables);

        // then
        assertThat(source).doesNotContain("style=").doesNotContain("onclick=").doesNotContain("onchange=")
                .doesNotContain("<script>").doesNotContain("th:field=").doesNotContain("class=\"button")
                .doesNotContain("class=\"box").doesNotContain("class=\"notification").doesNotContain("class=\"tag")
                .doesNotContain("class=\"select").doesNotContain("class=\"table").doesNotContain("class=\"title");
        Matcher ids = Pattern.compile(" id=\"([^\"]+)\"").matcher(html);
        Set<String> seen = new TreeSet<>();
        while (ids.find()) {
            assertThat(seen.add(ids.group(1))).as("duplicate id " + ids.group(1)).isTrue();
        }
        assertThat(OrderDetailsTemplateTest.occurrences(html, "<h1")).isEqualTo(1);
    }

    @Test
    void everyKeyOfTheTemplateExistsInBothBundles() throws IOException {
        // given
        String source = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");
        Set<String> missing = new TreeSet<>();

        // when
        Matcher keys = Pattern.compile("(?:#\\{|')(shipping\\.[a-zA-Z0-9_.]+|person\\.[a-zA-Z0-9_.]+)").matcher(source);
        while (keys.find()) {
            String key = keys.group(1);
            if (!pl.containsKey(key)) missing.add("pl:" + key);
            if (!en.containsKey(key)) missing.add("en:" + key);
        }
        for (String key : List.of("order.page.title", "rma.details", "nav.warehouse", "shipping.lead.rma.client",
                "shipping.lead.rma.center", "shipping.lead.warehouse", "shipping.lead.order")) {
            if (!pl.containsKey(key)) missing.add("pl:" + key);
            if (!en.containsKey(key)) missing.add("en:" + key);
        }

        // then
        assertThat(missing).isEmpty();
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                ShippingTemplateTest.class.getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    static ShipmentProposal allegroProposal() {
        return ShipmentProposal.available("Allegro One Box, One Kurier", "ALLEGRO", new DeliveryPoint("ALBOX-WAW-0231"),
                DeliveryType.LOCKER, List.of(new PackageOption("PACKAGE", new BigDecimal("64"), new BigDecimal("38"),
                        new BigDecimal("41"), new BigDecimal("25"))), new BigDecimal("5000"), new BigDecimal("5000"));
    }

    static Map<String, Object> allegroModel() {
        ShippingForm form = new ShippingForm(ORDER_ID, "orders");
        form.setProvider("allegro");
        form.setPackageTemplateId("t-pc");
        form.setCashOnDelivery(true);
        form.setCashOnDeliveryAmount(919.99);
        form.setParcels(new ArrayList<>(List.of(new ParcelForm(30, 20, 15, 2, 920, "Akcesoria", "package"))));
        Map<String, Object> variables = model(form, List.of(recipient("Katarzyna", "Złota 59")), orderView());
        List<ShippingIntegrationOption> options = List.of(
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null),
                ShippingIntegrationOption.available("allegro", "Wysyłam z Allegro", allegroProposal()).suggestedCopy());
        variables.put("integrationChoice", new ShippingIntegrationChoiceView(options, "allegro",
                "Podpowiadamy Wysyłam z Allegro, bo zamówienie jest z Allegro.", null));
        variables.put("allegroShipping", new AllegroShippingView("Allegro One Box, One Kurier", "One by Allegro",
                "ALBOX-WAW-0231", "shipping.allegro.deliveryType.LOCKER", "Katarzyna Wiśniewska · +48 512 345 678",
                "Limity metody Allegro One Box, One Kurier: najwyżej 64 × 38 × 41 cm, najwyżej 25 kg. Jedna paczka w przesyłce.",
                "Do zapłaty w zamówieniu: 919,99 PLN. Pobranie wypłaci Allegro. Najwyżej 5 000,00 PLN.",
                "Co najmniej kwota pobrania, najwyżej 5 000,00 PLN.", "shipping.allegro.labelFormat.PDF_A6",
                "/dashboard/store/shipping/allegro"));
        variables.put("allegroErrors", Map.of());
        return variables;
    }

    @Test
    void allegroOrderShowsTheChoiceAndTheOneStepAllegroForm() {
        // when
        String html = render(allegroModel());

        // then
        assertThat(html).contains("id=\"provider-form\"").contains(">Wyślij przez<")
                .contains("name=\"provider\" value=\"allegro\"").contains("checked")
                .contains("Podpowiadamy Wysyłam z Allegro, bo zamówienie jest z Allegro.")
                .contains("id=\"allegro-create-form\"")
                .contains("action=\"/dashboard/orders/" + ORDER_ID + "/shipping/allegro/create\"")
                .contains("<dt>Metoda dostawy</dt><dd>Allegro One Box, One Kurier</dd>")
                .contains("<dd>ALBOX-WAW-0231</dd>").contains("<dd>Automat paczkowy</dd>")
                .contains("najwyżej 64 × 38 × 41 cm").contains("Każdy karton to osobna przesyłka")
                .contains("name=\"parcels[0].depth\" value=\"20\"").contains("name=\"parcels[0].value\" value=\"920\"")
                .contains("name=\"cashOnDeliveryAmount\"").contains("wg cennika Allegro")
                .contains("PDF A6").contains("href=\"/dashboard/store/shipping/allegro\"")
                .contains(">Utwórz przesyłkę<")
                // the Furgonetka steps are not on the page
                .doesNotContain("id=\"shipping-estimate-form\"").doesNotContain("id=\"shipping-template-form\"")
                .doesNotContain("??");
    }

    @Test
    void allegroGreyedWithItsReasonForAShopOrder() {
        // given
        ShippingForm form = pricedOrderForm();
        form.setProvider("furgonetka");
        Map<String, Object> variables = model(form, List.of(recipient("Jan", "Polna 1")), orderView());
        variables.put("integrationChoice", new ShippingIntegrationChoiceView(List.of(
                ShippingIntegrationOption.unavailable("allegro", "Wysyłam z Allegro", "shipping.integration.reason.allegroOnly", null),
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null).suggestedCopy()),
                "furgonetka", null, null));

        // when
        String html = render(variables);

        // then
        assertThat(html).containsPattern("name=\"provider\" value=\"allegro\"[^>]*disabled")
                .contains("Tylko dla zamówień z Allegro.")
                .contains("id=\"shipping-estimate-form\"")
                .contains("type=\"hidden\" name=\"provider\" value=\"furgonetka\"");
    }

    @Test
    void allegroOrderSwitchedToFurgonetkaWarnsAboutTheBuyersMethod() {
        // given
        Map<String, Object> variables = model(pricedOrderForm(), List.of(recipient("Jan", "Polna 1")), orderView());
        variables.put("integrationChoice", new ShippingIntegrationChoiceView(List.of(
                ShippingIntegrationOption.available("allegro", "Wysyłam z Allegro", allegroProposal()).suggestedCopy(),
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null)),
                "furgonetka", null, "Kupujący wybrał Allegro One Box, One Kurier. Koszt pokryjesz z konta integracji Furgonetka, "
                        + "a numer przesyłki wyślemy do Allegro jak dziś."));

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("class=\"cl-alert is-warn\"").contains("Kupujący wybrał Allegro One Box, One Kurier.");
    }

    @Test
    void singleIntegrationHasNoChoiceCard() {
        // given
        Map<String, Object> variables = model(pricedOrderForm(), List.of(recipient("Jan", "Polna 1")), orderView());
        variables.put("integrationChoice", new ShippingIntegrationChoiceView(List.of(
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null).suggestedCopy()), "furgonetka", null, null));

        // when
        String html = render(variables);

        // then
        assertThat(html).doesNotContain("id=\"provider-form\"");
    }

    @Test
    void onlyAnUnavailableAllegroShowsItsReasonAndNeitherFormNorDefaultSteps() {
        // given
        Map<String, Object> variables = model(new ShippingForm(ORDER_ID, "orders"),
                List.of(recipient("Jan", "Polna 1")), orderView());
        variables.put("integrationChoice", new ShippingIntegrationChoiceView(List.of(
                ShippingIntegrationOption.unavailable("allegro", "Wysyłam z Allegro", "shipping.integration.reason.consent", null)),
                null, null, null));
        variables.put("shippingUnavailable", "Żadna integracja wysyłki nie nada tego zamówienia.");

        // when
        String html = SettingsTemplateRenderer.render("shipping", variables).replaceAll("\\s+", " ");

        // then
        assertThat(html).contains("id=\"provider-form\"").contains("Brak zgody na przesyłki w aplikacji Allegro")
                .containsPattern("name=\"provider\" value=\"allegro\"[^>]*disabled")
                .contains("id=\"shipping-unavailable\"").contains("Żadna integracja wysyłki nie nada tego zamówienia.")
                .doesNotContain("id=\"shipping-template-form\"").doesNotContain("id=\"allegro-create-form\"")
                .doesNotContain("Wczytaj paczki");
    }

    @Test
    void whenTheDefaultAndAllegroAreBothUnavailableBothReasonsShowAndNoStepsDo() {
        // given
        Map<String, Object> variables = model(new ShippingForm(ORDER_ID, "orders"),
                List.of(recipient("Jan", "Polna 1")), orderView());
        variables.put("integrationChoice", new ShippingIntegrationChoiceView(List.of(
                ShippingIntegrationOption.unavailable("furgonetka", "Furgonetka", "shipping.integration.reason.notConnected", null),
                ShippingIntegrationOption.unavailable("allegro", "Wysyłam z Allegro", "shipping.integration.reason.authLost", null)),
                null, null, null));
        variables.put("shippingUnavailable", "Żadna integracja wysyłki nie nada tego zamówienia.");

        // when
        String html = SettingsTemplateRenderer.render("shipping", variables).replaceAll("\\s+", " ");

        // then
        assertThat(html).contains("Integracja nie jest podłączona.").contains("Połączenie z Allegro wygasło")
                .contains("id=\"shipping-unavailable\"").doesNotContain("id=\"shipping-template-form\"")
                .doesNotContain("Wczytaj paczki");
    }

    @Test
    void allegroFieldErrorsAreShownNextToTheirFields() {
        // given
        Map<String, Object> variables = allegroModel();
        variables.put("allegroErrors", Map.of("insurance", "Ubezpieczenie musi wynosić co najmniej kwotę pobrania (920 PLN)."));

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("id=\"allegro-insurance-error\"")
                .contains("Ubezpieczenie musi wynosić co najmniej kwotę pobrania (920 PLN).")
                .containsPattern("id=\"allegro-insurance\"[^>]*aria-invalid=\"true\"");
    }

    @Test
    void cashOnDeliveryTellsThatAllegroPaysItOutToTheSellersAllegroFunds() {
        // when
        String html = render(allegroModel());

        // then
        assertThat(html).contains("id=\"allegro-cod-payout\"")
                .contains("Pobranie wypłaci Allegro na Twoje środki w Allegro.")
                .doesNotContain("konto sklepu").doesNotContain("konta bankowego");
    }
}
