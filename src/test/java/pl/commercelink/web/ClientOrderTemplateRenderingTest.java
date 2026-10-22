package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.stores.Branding;
import pl.commercelink.web.clientorder.ClientOrderNotice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOrderTemplateRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final Pattern STAGE = Pattern.compile("<li class=\"cl-stage[^\"]*\"[^>]*>");
    private static final LocalDateTime SHIPPED = LocalDateTime.of(2026, 10, 23, 14, 10);

    @Test
    void headlineIsTheStatusSentence() {
        // when
        String polish = ClientOrderRenderer.newOrder().locale(PL).render();
        String english = ClientOrderRenderer.newOrder().render();

        // then
        assertThat(polish).contains("<title>Zamówienie 7f3a91c2 · Nova Tech</title>")
                .contains("<p class=\"cl-eyebrow\">Zamówienie 7f3a91c2 · złożone 14 października 2026</p>")
                .contains("<h1 class=\"cl-page-title\">Kompletujemy Twoje zamówienie</h1>")
                .contains("Część produktów jest w drodze od dostawcy, resztę mamy już u siebie.")
                .contains("Planowana wysyłka: piątek 23 października.");
        assertThat(polish.split("<h1", -1)).hasSize(2);
        assertThat(english).contains("<h1 class=\"cl-page-title\">We are assembling your order</h1>")
                .contains("<main id=\"clContent\" class=\"cl-page cl-public-main is-form\"");
    }

    @Test
    void chosenDayReplacesThePlannedOneInTheLead() {
        // when
        String html = ClientOrderRenderer.newOrder().preferredShippingAt(LocalDate.of(2026, 10, 27)).locale(PL).render();

        // then
        assertThat(html).contains("Twój dzień wysyłki: wtorek 27 października.")
                .doesNotContain("Planowana wysyłka: piątek");
    }

    @Test
    void currentStageIsMarkedForAssistiveTech() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        List<String> stages = STAGE.matcher(html).results().map(MatchResult::group).toList();
        assertThat(stages).hasSize(5);
        assertThat(stages.get(0)).contains("is-done").doesNotContain("aria-current");
        assertThat(stages.get(1)).contains("is-current").contains("aria-current=\"step\"");
        assertThat(html.split("aria-current=\"step\"", -1)).hasSize(2);
        assertThat(html).contains("<span class=\"cl-visually-hidden\">ukończony: </span>")
                .contains("<span class=\"cl-status is-info\">W kompletacji</span>")
                .contains("<span class=\"cl-stage-when\">14 paź</span>");
    }

    @Test
    void factsShowPlannedDates() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html).contains("<dl class=\"cl-facts\">")
                .contains("<dt>Planowana kompletacja</dt>")
                .contains("<dd>śr. 21 października</dd>")
                .contains("<dt>Planowana wysyłka</dt>")
                .contains("<dd>pt. 23 października</dd>")
                .contains("<dt>Sposób dostawy</dt>")
                .contains("<dd>DPD courier</dd>");
    }

    @Test
    void collectionShowsReadinessAndItsOwnStageNames() {
        // given
        Shipment ready = ClientOrderRenderer.shipment(null, null, null, LocalDateTime.of(2026, 10, 23, 12, 0), null);
        ready.setType(ShipmentType.PersonalCollection);

        // when
        String html = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping).shipments(ready).locale(PL).render();

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Zamówienie czeka na odbiór</h1>")
                .contains("<dt>Gotowe od</dt>")
                .contains("<dd>pt. 23 października, 12:00</dd>")
                .contains("<span class=\"cl-stage-name\">Gotowe do odbioru</span>")
                .doesNotContain("order-shipments-title");
    }

    @Test
    void cancelledShowsAlertAndNoProgress() {
        // when
        String html = ClientOrderRenderer.newOrder().status(OrderStatus.Cancelled).locale(PL).render();

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Zamówienie anulowane</h1>")
                .contains("cl-alert is-bad")
                .contains("To zamówienie zostało anulowane.")
                .doesNotContain("cl-stages")
                .doesNotContain("cl-page-lead")
                .doesNotContain("id=\"payment\"")
                .doesNotContain("id=\"date\"")
                .doesNotContain("Skompletowany");
    }

    @Test
    void twoShipmentsTitleCountsThem() {
        // when
        String two = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dpd", "111", "https://track/111", SHIPPED, null),
                        ClientOrderRenderer.shipment("dpd", "222", "https://track/222", SHIPPED, null))
                .locale(PL).render();
        String one = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dpd", "111", "https://track/111", SHIPPED, null))
                .locale(PL).render();

        // then
        assertThat(two).contains("id=\"order-shipments-title\">Przesyłki: 2</h2>");
        assertThat(one).contains("id=\"order-shipments-title\">Przesyłka</h2>");
    }

    @Test
    void shipmentWithoutUrlShowsNumberWithoutTrackButton() {
        // when
        String html = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dpd", "1000634722104", null, SHIPPED, null))
                .locale(PL).render();

        // then
        assertThat(html).contains("Numer: 1000634722104")
                .contains("data-cl-copy=\"1000634722104\"")
                .contains("nadana 23 paź, 14:10")
                .doesNotContain("Śledź przesyłkę");
    }

    @Test
    void deliveredShipmentHasAPillAndBothDates() {
        // when
        String html = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dpd", "111", null, SHIPPED, LocalDateTime.of(2026, 10, 24, 11, 32)))
                .locale(PL).render();

        // then
        assertThat(html).contains("<span class=\"cl-status is-ok\">Dostarczona</span>")
                .contains("doręczona 24 paź, 11:32")
                .contains("fas fa-check");
    }

    @Test
    void trackLinkAnnouncesNewTab() {
        // when
        String html = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dpd", "111", "https://tracktrace.dpd.com.pl/111", SHIPPED, null))
                .locale(PL).render();

        // then
        assertThat(html).containsPattern("<a class=\"cl-button\" href=\"https://tracktrace.dpd.com.pl/111\" target=\"_blank\" rel=\"noopener\">")
                .contains("Śledź przesyłkę")
                .contains("<span class=\"cl-visually-hidden\"> (otwiera się w nowej karcie)</span>");
    }

    @Test
    void carrierCodeShownAsName() {
        // when
        String html = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dhl", "111", null, SHIPPED, null))
                .locale(PL).render();

        // then
        assertThat(html).containsPattern("<p class=\"cl-list-title\">\\s*DHL");
    }

    @Test
    void noShipmentsCardBeforeDispatch() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html).doesNotContain("order-shipments-title").doesNotContain("Numer przesyłki i link");
    }

    @Test
    void itemsHaveStatusPills() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html).contains("id=\"order-items-title\">Pozycje: 4</h2>")
                .contains("<p class=\"cl-card-desc\">Skompletowane: 2 z 4</p>")
                .contains("<span class=\"cl-status is-ok\">Skompletowany</span>")
                .contains("<span class=\"cl-status is-info\">Oczekujemy na dostawę</span>")
                .contains("<span class=\"cl-status is-neutral\">W alokacji</span>")
                .contains("Processor · 1 szt.")
                .contains("<p class=\"cl-list-desc\">1 szt.</p>");
    }

    @Test
    void onlyProductsAreListed() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html).contains("AMD Ryzen 7 7800X3D").doesNotContain("PC assembly service");
    }

    @Test
    void contactEmailIsEscaped() {
        // when
        String html = ClientOrderRenderer.newOrder().contactEmail("\"><script>alert(1)</script>@x.pl").locale(PL).render();

        // then
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>alert");
    }

    @Test
    void contactSentenceLinksTheStoreAndQuotesTheNumber() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html).contains("id=\"order-help-title\">Masz pytanie?</h2>")
                .contains("<a href=\"mailto:biuro@novatech.pl\">biuro@novatech.pl</a>")
                .contains("<strong>7f3a91c2</strong>");
    }

    @Test
    void pageHasNoInlineStyleBesidesTheFrame() throws IOException {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html.split("style=\"", -1)).hasSize(2);
        assertThat(html).containsPattern("<body class=\"cl-public\" style=\"")
                .doesNotContain("<style")
                .doesNotContainPattern("<script(?![^>]*\\ssrc=)[^>]*>");
        for (Path source : templateSources()) {
            assertThat(Files.readString(source)).as(source.toString())
                    .doesNotContain("style=").doesNotContain("th:style").doesNotContain("th:utext").doesNotContain("branding");
        }
    }

    @Test
    void logoIsInTheHeaderNotAtTheBottom() {
        // given
        ClientOrderRenderer renderer = ClientOrderRenderer.newOrder();
        Branding branding = new Branding();
        branding.setLogo("logo.png");
        renderer.store().setBranding(branding);

        // when
        String html = renderer.locale(PL).render();

        // then
        assertThat(html.indexOf("class=\"cl-public-logo\"")).isPositive().isLessThan(html.indexOf("id=\"clContent\""));
        assertThat(html.split("/StoreLogo/", -1)).hasSize(2);
    }

    @Test
    void paymentDueComesBeforeDeliveryAndDate() {
        // when
        String html = ClientOrderRenderer.newOrder().payments(Payment.bankTransfer("REF-1", "Zaliczka", 3000.0)).locale(PL).render();

        // then
        assertThat(html.indexOf("id=\"payment\"")).isPositive().isLessThan(html.indexOf("id=\"delivery\""));
        assertThat(html.indexOf("id=\"delivery\"")).isLessThan(html.indexOf("id=\"date\""));
        assertThat(html.indexOf("id=\"date\"")).isLessThan(html.indexOf("order-items-title"));
    }

    @Test
    void paymentWithoutBalanceComesAfterTheDate() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html.indexOf("id=\"date\"")).isPositive().isLessThan(html.indexOf("id=\"payment\""));
        assertThat(html.indexOf("id=\"payment\"")).isLessThan(html.indexOf("order-items-title"));
        assertThat(html.indexOf("order-items-title")).isLessThan(html.indexOf("order-help-title"));
    }

    @Test
    void deliveryComesFirstInTheAddressSteps() {
        // when
        String code = ClientOrderRenderer.newOrder().addressStep("code").locale(PL).render();
        String notice = ClientOrderRenderer.newOrder()
                .notice(ClientOrderNotice.ok(ClientOrderNotice.DELIVERY, "Adres zmieniony")).locale(PL).render();

        // then
        assertThat(code.indexOf("id=\"delivery\"")).isPositive().isLessThan(code.indexOf("order-progress-title"));
        assertThat(notice.indexOf("id=\"delivery\"")).isPositive().isLessThan(notice.indexOf("order-progress-title"));
        assertThat(notice).contains("Adres zmieniony");
    }

    @Test
    void formsKeepTheirEndpointsAndFieldNames() {
        // when
        String start = ClientOrderRenderer.newOrder().locale(PL).render();
        String code = ClientOrderRenderer.newOrder().addressStep("code").locale(PL).render();
        String edit = ClientOrderRenderer.newOrder().addressStep("edit").locale(PL).render();

        // then
        assertThat(start).contains("action=\"" + ClientOrderRenderer.ORDER_PATH + "/address/request-code\"")
                .contains("action=\"" + ClientOrderRenderer.ORDER_PATH + "/preferred-shipping\"")
                .contains("name=\"date\"");
        assertThat(code).contains("action=\"" + ClientOrderRenderer.ORDER_PATH + "/address/confirm-code\"")
                .contains("name=\"v\" value=\"ver-1\"")
                .contains("name=\"code\"");
        assertThat(edit).contains("action=\"" + ClientOrderRenderer.ORDER_PATH + "/address\"")
                .contains("name=\"t\" value=\"tok-1\"");
        for (String field : List.of("name", "surname", "companyName", "streetAndNumber", "postalCode", "city", "email", "phone")) {
            assertThat(edit).contains("name=\"" + field + "\"");
        }
    }

    @Test
    void addressIsMaskedExceptPostalCodeCityAndCountry() {
        // when
        String html = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        assertThat(html).doesNotContain("Kowalski").doesNotContain("Miodowa 14").doesNotContain("600100789")
                .doesNotContain("jan.kowalski@gmail.com")
                .contains("<h3>Adres dostawy</h3>")
                .contains("<p>00-251 Warszawa, PL</p>");
    }

    @Test
    void changeAddressButtonOnlyWhenEditable() {
        // when
        String editable = ClientOrderRenderer.newOrder().locale(PL).render();
        String shipped = ClientOrderRenderer.newOrder().status(OrderStatus.Shipping)
                .shipments(ClientOrderRenderer.shipment("dpd", "111", null, SHIPPED, null)).locale(PL).render();
        String collection = ClientOrderRenderer.newOrder().personalCollection().locale(PL).render();

        // then
        assertThat(editable).containsPattern("<form method=\"post\" data-cl-submit-once action=\"[^\"]*/address/request-code\">\\s*"
                        + "<button class=\"cl-button\" type=\"submit\"><i class=\"fas fa-pencil-alt\" aria-hidden=\"true\"></i>\\s*<span>Zmień adres</span>")
                .contains("<p class=\"cl-help\">Adres możesz zmienić do nadania paczki. Wyślemy kod potwierdzający na e-mail podany w zamówieniu.</p>");
        for (String html : List.of(shipped, collection)) {
            assertThat(html).doesNotContain("/address/request-code").doesNotContain("Zmień adres")
                    .doesNotContain("Adres możesz zmienić");
        }
    }

    @Test
    void codeStepShowsStepOneOfTwoAndCodeField() {
        // when
        String html = ClientOrderRenderer.newOrder().addressStep("code").locale(PL).render();

        // then
        assertThat(html).containsPattern("<p class=\"cl-substep\"><span class=\"cl-step-badge\" aria-hidden=\"true\">1</span>\\s*Krok 1 z 2</p>")
                .contains("<h3 class=\"cl-fieldset-title\">Potwierdź, że to Ty</h3>")
                .contains("<input type=\"hidden\" name=\"v\" value=\"ver-1\">")
                .containsPattern("<input id=\"code\" name=\"code\" class=\"cl-input cl-code-input\" type=\"text\" maxlength=\"6\" required"
                        + "\\s+autocomplete=\"one-time-code\" autocapitalize=\"characters\" spellcheck=\"false\"\\s+autofocus")
                .contains("class=\"cl-code-row\"")
                .containsPattern("Sprawdź folder spam albo\\s*<button class=\"cl-link-button\" type=\"submit\">wyślij kod ponownie</button>.")
                .doesNotContain("Zmień adres");
        assertThat(html.split("data-cl-submit-once", -1)).hasSize(4);
    }

    @Test
    void codeErrorIsAttachedToTheField() {
        // when
        String html = ClientOrderRenderer.newOrder().addressStep("code")
                .notice(ClientOrderNotice.codeError("Nieprawidłowy kod. Spróbuj ponownie.")).locale(PL).render();

        // then
        assertThat(html).contains("aria-invalid=\"true\"").contains("aria-describedby=\"code-error\"")
                .containsPattern("<p class=\"cl-field-error\" id=\"code-error\">\\s*<i class=\"fas fa-exclamation-circle\" aria-hidden=\"true\"></i>"
                        + "<span>Nieprawidłowy kod. Spróbuj ponownie.</span>")
                .doesNotContain("autofocus")
                .doesNotContain("cl-alert is-bad");
        assertThat(html.indexOf("class=\"cl-code-row\"")).isLessThan(html.indexOf("id=\"code-error\""));
    }

    @Test
    void editStepPostsAllAddressFieldsAndToken() {
        // when
        String html = ClientOrderRenderer.newOrder().addressStep("edit").locale(PL).render();

        // then
        assertThat(html).containsPattern("<p class=\"cl-substep\"><span class=\"cl-step-badge\" aria-hidden=\"true\">2</span>\\s*Krok 2 z 2</p>")
                .contains("<h3 class=\"cl-fieldset-title\">Nowy adres dostawy</h3>")
                .containsPattern("<form method=\"post\" data-cl-submit-once action=\"[^\"]*/address\">")
                .contains("<input type=\"hidden\" name=\"t\" value=\"tok-1\">")
                .contains("value=\"Kowalski\"")
                .doesNotContain("<h3>Adres dostawy</h3>");
        for (String field : List.of("name", "surname", "companyName", "streetAndNumber", "postalCode", "city", "email", "phone")) {
            assertThat(html).contains("name=\"" + field + "\"");
        }
        for (String required : List.of("streetAndNumber", "postalCode", "city", "email", "phone")) {
            assertThat(html).containsPattern("name=\"" + required + "\"[^>]*required");
        }
        assertThat(html).containsPattern("autocomplete=\"shipping postal-code\"")
                .containsPattern("<div class=\"cl-span-2\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"postalCode\">")
                .containsPattern("<div class=\"cl-span-4\">\\s*<div class=\"cl-field\">\\s*<label class=\"cl-label\" for=\"city\">");
    }

    @Test
    void editStepShowsCountryAsText() {
        // when
        String polish = ClientOrderRenderer.newOrder().addressStep("edit").locale(PL).render();
        String english = ClientOrderRenderer.newOrder().addressStep("edit").render();

        // then
        assertThat(polish).contains("<p class=\"cl-static-value\">Polska</p>").doesNotContain("name=\"country\"");
        assertThat(english).contains("<p class=\"cl-static-value\">Poland</p>");
    }

    @Test
    void deliveryNoticeRendersInTheDeliveryCard() {
        // when
        String edit = ClientOrderRenderer.newOrder().addressStep("edit")
                .notice(ClientOrderNotice.bad(ClientOrderNotice.DELIVERY, "Uzupełnij poprawnie wszystkie wymagane pola adresu."))
                .locale(PL).render();
        String saved = ClientOrderRenderer.newOrder()
                .notice(ClientOrderNotice.ok(ClientOrderNotice.DELIVERY, "Adres dostawy został zmieniony.")).locale(PL).render();

        // then
        assertThat(edit).contains("Krok 2 z 2");
        int card = edit.indexOf("id=\"delivery\"");
        int alert = edit.indexOf("<div class=\"cl-alert is-bad\" role=\"status\" tabindex=\"-1\">");
        assertThat(alert).isGreaterThan(edit.indexOf("Krok 2 z 2")).isGreaterThan(card)
                .isLessThan(edit.indexOf("name=\"name\"")).isLessThan(edit.indexOf("order-progress-title"));
        assertThat(edit.split("cl-alert is-bad", -1)).hasSize(2);
        assertThat(saved).contains("<div class=\"cl-alert is-ok\" role=\"status\" tabindex=\"-1\">")
                .contains("Adres dostawy został zmieniony.");
        assertThat(saved.indexOf("cl-alert is-ok")).isGreaterThan(saved.indexOf("id=\"delivery\""))
                .isLessThan(saved.indexOf("<h3>Adres dostawy</h3>"));
    }

    @Test
    void pickupPointShowsMaskedPointCode() {
        // when
        String html = ClientOrderRenderer.newOrder().pickupPoint("WAW01M").locale(PL).render();

        // then
        assertThat(html).contains("<h3>Punkt odbioru</h3>")
                .containsPattern("<p>InPost · <span>\\s*<span>WAW\\*\\*\\*</span>")
                .doesNotContain("WAW01M")
                .doesNotContain("<p>inpost</p>");
    }

    @Test
    void personalCollectionShowsPickupAddress() {
        // given
        ClientOrderRenderer renderer = ClientOrderRenderer.newOrder().personalCollection();
        BillingDetails company = new BillingDetails();
        company.setCompanyName("Nova Tech sp. z o.o.");
        company.setStreetAndNumber("ul. Prosta 12");
        company.setPostalCode("00-850");
        company.setCity("Warszawa");
        renderer.store().setBillingDetails(company);

        // when
        String html = renderer.locale(PL).render();

        // then
        assertThat(html).contains("<h3>Adres odbioru</h3>")
                .contains("<p>Nova Tech sp. z o.o.</p>")
                .contains("<p>ul. Prosta 12</p>")
                .contains("<p>00-850 Warszawa</p>")
                .doesNotContain("<h3>Adres dostawy</h3>");
    }

    @Test
    void deliveryCardComesFirstDuringAddressChange() {
        // when
        String code = ClientOrderRenderer.newOrder().addressStep("code").locale(PL).render();
        String edit = ClientOrderRenderer.newOrder().addressStep("edit").locale(PL).render();
        String plain = ClientOrderRenderer.newOrder().locale(PL).render();

        // then
        for (String html : List.of(code, edit)) {
            assertThat(html.indexOf("id=\"delivery\"")).isPositive().isLessThan(html.indexOf("order-progress-title"));
            assertThat(html.split("id=\"delivery\"", -1)).hasSize(2);
        }
        assertThat(plain.indexOf("id=\"delivery\"")).isGreaterThan(plain.indexOf("order-progress-title"));
    }

    static List<Path> templateSources() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        try (Stream<Path> fragments = Files.list(templates.resolve("client-order"))) {
            return Stream.concat(Stream.of(templates.resolve("clientOrder.html")),
                    fragments.filter(path -> path.toString().endsWith(".html")).sorted()).toList();
        }
    }
}
