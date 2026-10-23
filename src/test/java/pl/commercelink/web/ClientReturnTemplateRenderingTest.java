package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.Parcel;
import pl.commercelink.web.clientreturn.ClientReturnNotice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static pl.commercelink.web.ClientReturnRenderer.RETURN_PATH;
import static pl.commercelink.web.publicpage.PublicTemplateRenderer.main;

class ClientReturnTemplateRenderingTest {

    private static final String FIELDS = "name=\"shippingDetails.";

    @Test
    void bookStateShowsTheFormFirst() {
        // when
        String html = ClientReturnRenderer.approved().render();

        // then
        assertThat(html).contains("<title>Zwrot 3f9c2a71 · Nova Tech</title>")
                .contains("<p class=\"cl-eyebrow\">Zwrot 3f9c2a71 · zgłoszony 21 października 2026 · zamówienie 7f3a91c2</p>")
                .contains("<h1 class=\"cl-page-title\">Zamów kuriera po paczkę ze zwrotem</h1>")
                .contains("<p class=\"cl-page-lead\">Sklep przyjął Twoje zgłoszenie. Wybierz karton i podaj adres — kurier odbierze paczkę spod drzwi.</p>");
        assertThat(html.split("<h1", -1)).hasSize(2);
        String form = card(html, "return-form");
        assertThat(form).contains("<h2 class=\"cl-card-title\" id=\"return-form-title\">Paczka i adres odbioru</h2>")
                .contains("action=\"" + RETURN_PATH + "/submit\"")
                .contains("data-cl-submit-once")
                .contains("novalidate")
                .contains("<legend class=\"cl-fieldset-title\">Paczka</legend>")
                .contains("<legend class=\"cl-fieldset-title\">Skąd kurier odbierze paczkę</legend>")
                .contains("<button class=\"cl-button is-primary\" type=\"submit\">Zamów kuriera</button>")
                .contains("Kurier przyjedzie pod ten adres. Numer przesyłki i termin odbioru wyślemy na podany e-mail.")
                .contains("<p class=\"cl-static-value\">Polska</p>")
                .doesNotContain("name=\"shippingDetails.country\"");
        for (String field : List.of("name", "surname", "companyName", "streetAndNumber", "postalCode", "city", "email", "phone")) {
            assertThat(form).contains(FIELDS + field + "\"");
        }
        assertThat(form).contains("autocomplete=\"section-return given-name\"")
                .contains("type=\"email\"")
                .contains("type=\"tel\"")
                .contains("maxlength=\"100\"");
        assertThat(main(html)).containsPattern("<div class=\"cl-stack\">\\s*<section[^>]*id=\"return-form\"");
    }

    @Test
    void oneTemplateIsASentenceWithAHiddenField() {
        // given
        ClientReturnRenderer renderer = ClientReturnRenderer.approved()
                .templates(ClientReturnRenderer.template("t-s", "RMA - karton S", true, new Parcel(25, 15, 10, 1, 0, null)));

        // when
        String form = card(renderer.render(), "return-form");

        // then
        assertThat(form).contains("<input type=\"hidden\" name=\"selectedPackageTemplateId\" value=\"t-s\">")
                .contains("<p class=\"cl-note\" id=\"selectedPackageTemplateId\">Paczka: <strong>Karton S</strong> · 25 × 15 × 10 cm · 1 kg</p>")
                .doesNotContain("cl-choice-cards")
                .doesNotContain("<select");
    }

    @Test
    void threeTemplatesAreChoiceCards() {
        // when
        String form = card(ClientReturnRenderer.approved().render(), "return-form");

        // then
        assertThat(form).contains("<fieldset class=\"cl-choice-cards\">")
                .contains("<legend>Jaki karton przygotujesz?</legend>")
                .contains("<span class=\"cl-choice-title\">Karton S</span>")
                .contains("<span class=\"cl-choice-description\">25 × 15 × 10 cm · 1 kg</span>")
                .contains("<span class=\"cl-choice-title\">Karton L na kartę graficzną</span>")
                .contains("<span class=\"cl-choice-description\">Paczki: 2 · 80 × 40 × 60 cm · 12 kg + 60 × 40 × 40 cm · 5 kg</span>")
                .containsPattern("<input type=\"radio\" name=\"selectedPackageTemplateId\" value=\"t-s\" id=\"selectedPackageTemplateId\" checked=\"checked\">")
                .contains("<input type=\"radio\" name=\"selectedPackageTemplateId\" value=\"t-m\">")
                .doesNotContain("RMA - ")
                .doesNotContain("Karton wysyłkowy");
    }

    @Test
    void sixTemplatesAreASelect() {
        // given
        ClientReturnRenderer renderer = ClientReturnRenderer.approved().templates(
                ClientReturnRenderer.template("t1", "RMA - Karton 1", false, new Parcel(10, 10, 10, 1, 0, null)),
                ClientReturnRenderer.template("t2", "RMA - Karton 2", true, new Parcel(20, 20, 20, 2, 0, null)),
                ClientReturnRenderer.template("t3", "RMA - Karton 3", false),
                ClientReturnRenderer.template("t4", "RMA - Karton 4", false, new Parcel(40, 40, 40, 4, 0, null),
                        new Parcel(41, 41, 41, 4, 0, null)),
                ClientReturnRenderer.template("t5", "RMA - Karton 5", false),
                ClientReturnRenderer.template("t6", "RMA - Karton 6", false));

        // when
        String form = card(renderer.render(), "return-form");

        // then
        assertThat(form).contains("<select class=\"cl-select\" id=\"selectedPackageTemplateId\" name=\"selectedPackageTemplateId\"")
                .contains("<span>Jaki karton przygotujesz?</span>")
                .contains("<option value=\"t2\" selected=\"selected\">Karton 2 · 20 × 20 × 20 cm · 2 kg</option>")
                .contains("<option value=\"t3\">Karton 3</option>")
                .contains("<option value=\"t4\">Karton 4 · Paczki: 2 · 40 × 40 × 40 cm · 4 kg + 41 × 41 × 41 cm · 4 kg</option>")
                .doesNotContain("cl-choice-cards");
    }

    @Test
    void rejectedFormKeepsTheValuesAndShowsErrorsByTheFields() {
        // given
        ShippingDetails posted = ClientReturnRenderer.address();
        posted.setName("Jan <b>");
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("shippingDetails.postalCode", "client.return.form.postal.invalid");
        errors.put("shippingDetails.email", "client.return.form.email.invalid");
        ClientReturnRenderer renderer = ClientReturnRenderer.approved().refused(posted, "t-m", errors);

        // when
        String form = card(renderer.render(), "return-form");

        // then
        assertThat(form).contains("<p class=\"cl-alert-title\">Popraw dane, zanim zamówimy kuriera</p>")
                .contains("<a href=\"#shippingDetails.postalCode\">Kod pocztowy: Podaj kod w formacie 00-000.</a>")
                .contains("<a href=\"#shippingDetails.email\">E-mail: Podaj pełny adres e-mail, np. jan@example.com.</a>")
                .contains("value=\"Jan &lt;b&gt;\"")
                .contains("value=\"00950\"")
                .contains("value=\"Marszałkowska 140/12\"")
                .containsPattern("id=\"shippingDetails.postalCode\"[^>]*aria-invalid=\"true\"[^>]*aria-describedby=\"shippingDetails.postalCode-error\"")
                .contains("<p class=\"cl-field-error\" id=\"shippingDetails.postalCode-error\">")
                .contains("value=\"t-m\" id=\"selectedPackageTemplateId\" checked=\"checked\"")
                .doesNotContain("Jan <b>");
        assertThat(form.indexOf("cl-alert-title")).isLessThan(form.indexOf("cl-choice-cards"));
    }

    @Test
    void wrongPackageErrorStandsByThePackageChoice() {
        // given
        ClientReturnRenderer renderer = ClientReturnRenderer.approved().refused(ClientReturnRenderer.address(), "gone",
                Map.of("selectedPackageTemplateId", "client.return.form.package.invalid"));

        // when
        String form = card(renderer.render(), "return-form");

        // then
        assertThat(form).contains("<a href=\"#selectedPackageTemplateId\">Paczka: Wybierz karton z listy.</a>")
                .contains("<p class=\"cl-field-error\" id=\"selectedPackageTemplateId-error\">")
                .contains("<fieldset class=\"cl-choice-cards\" aria-describedby=\"selectedPackageTemplateId-error\">");
    }

    @Test
    void noticeStandsInItsCard() {
        // given
        ClientReturnRenderer renderer = ClientReturnRenderer.approved()
                .notice(ClientReturnNotice.bad(ClientReturnNotice.FORM, "Przewoźnik nie przyjął zamówienia <i>."));

        // when
        String html = renderer.render();

        // then
        String form = card(html, "return-form");
        assertThat(form).contains("<div class=\"cl-alert is-bad\" role=\"status\" tabindex=\"-1\">")
                .contains("<p class=\"cl-alert-text\">Przewoźnik nie przyjął zamówienia &lt;i&gt;.</p>");
        assertThat(main(html).split("cl-alert-text", -1)).hasSize(2);
        assertThat(form.indexOf("cl-alert is-bad")).isLessThan(form.indexOf("cl-fieldset"));
    }

    @Test
    void noticeWithoutItsCardStandsAboveTheFirstCard() {
        // given — the store already has the address, so the page has no form and no parcel yet
        ClientReturnRenderer renderer = ClientReturnRenderer.approved()
                .notice(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "Sklep zamówi kuriera po paczkę."));

        // when
        String html = ClientReturnRenderer.approved().savedAddress(ClientReturnRenderer.address())
                .notice(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "Sklep zamówi kuriera po paczkę.")).render();
        String withForm = renderer.render();

        // then
        assertThat(main(html)).containsPattern("<div class=\"cl-stack\">\\s*<div class=\"cl-alert is-info\" role=\"status\" tabindex=\"-1\">")
                .contains("<p class=\"cl-alert-text\">Sklep zamówi kuriera po paczkę.</p>");
        assertThat(main(withForm).indexOf("cl-alert is-info")).isPositive()
                .isLessThan(main(withForm).indexOf("id=\"return-form\""));
    }

    @Test
    void successNoticeIsGreen() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems)
                .notice(ClientReturnNotice.ok(ClientReturnNotice.PARCEL, "Zamówiliśmy kuriera.")).render();

        // then
        assertThat(main(html)).contains("<div class=\"cl-alert is-ok\" role=\"status\" tabindex=\"-1\">")
                .contains("<i class=\"fas fa-check-circle\"></i>");
    }

    @Test
    void bookUnavailableShowsTheAlertWithoutFields() {
        // when
        String html = ClientReturnRenderer.approved().noCarrier().render();

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Przyjęliśmy Twoje zgłoszenie zwrotu</h1>")
                .contains("<p class=\"cl-page-lead\">Kuriera po paczkę zamówisz, gdy sklep ustawi wysyłkę zwrotów.</p>");
        String form = card(html, "return-form");
        assertThat(form).contains("Paczka i adres odbioru")
                .contains("<div class=\"cl-alert is-warn\">")
                .contains("<p class=\"cl-alert-title\">Nie możesz teraz zamówić kuriera</p>")
                .contains("Sklep nie ma jeszcze ustawionej wysyłki zwrotów. Napisz do nas:")
                .contains("<a href=\"mailto:biuro@novatech.pl\">biuro@novatech.pl</a>")
                .doesNotContain("<form")
                .doesNotContain("<input")
                .doesNotContain("<button");
    }

    @Test
    void bookUnavailableWithoutAnEmailSendsTheCustomerToTheSeller() {
        // when
        String html = ClientReturnRenderer.approved().noCarrier().contactEmail(null).render();

        // then
        assertThat(card(html, "return-form")).contains("skontaktuj się ze sprzedawcą")
                .doesNotContain("mailto:");
    }

    @Test
    void englishTexts() {
        // when
        String html = ClientReturnRenderer.approved().locale(Locale.ENGLISH).render();

        // then
        assertThat(html).contains("<title>Return 3f9c2a71 · Nova Tech</title>")
                .contains("<p class=\"cl-eyebrow\">Return 3f9c2a71 · reported 21 October 2026 · order 7f3a91c2</p>")
                .contains("<h1 class=\"cl-page-title\">Book a courier for your return</h1>")
                .contains("The store accepted your request. Choose a box and give the address — the courier collects the parcel at your door.")
                .contains("<h2 class=\"cl-card-title\" id=\"return-form-title\">Parcel and pickup address</h2>")
                .contains("<legend>Which box will you use?</legend>")
                .contains("<p class=\"cl-static-value\">Poland</p>")
                .contains("<button class=\"cl-button is-primary\" type=\"submit\">Book a courier</button>")
                .doesNotContain("??");
    }

    @Test
    void eyebrowLeavesOutWhatTheReturnDoesNotHave() {
        // when
        String noOrder = ClientReturnRenderer.approved().orderId(null).render();
        String noDate = ClientReturnRenderer.approved().createdAt(null).render();
        String bare = ClientReturnRenderer.approved().orderId(null).createdAt(null).render();

        // then
        assertThat(noOrder).contains("<p class=\"cl-eyebrow\">Zwrot 3f9c2a71 · zgłoszony 21 października 2026</p>");
        assertThat(noDate).contains("<p class=\"cl-eyebrow\">Zwrot 3f9c2a71 · zamówienie 7f3a91c2</p>");
        assertThat(bare).contains("<p class=\"cl-eyebrow\">Zwrot 3f9c2a71</p>");
    }

    static Stream<Arguments> states() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
        Supplier<Shipment> creating = () -> {
            Shipment shipment = new Shipment(ShipmentType.Courier);
            shipment.setProvider("furgonetka");
            shipment.setCreation(ShipmentCreationState.pending("cmd", now));
            return shipment;
        };
        Supplier<Shipment> tracked = () -> {
            Shipment shipment = new Shipment(ShipmentType.Courier);
            shipment.setProvider("furgonetka");
            shipment.setTrackingNo("00007731440201U");
            return shipment;
        };
        Supplier<Shipment> failed = () -> {
            Shipment shipment = creating.get();
            shipment.setCreation(ShipmentCreationState.pending("cmd", now).failed("refused"));
            return shipment;
        };
        return Stream.of(
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().savedAddress(ClientReturnRenderer.address()),
                        "Sklep zamówi kuriera po paczkę",
                        "Adres odbioru jest już zapisany. Numer przesyłki i termin odbioru wyślemy e-mailem."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().shipments(creating.get()),
                        "Zamawiamy kuriera po Twoją paczkę",
                        "Zapakuj produkty. Numer przesyłki i termin odbioru dostaniesz e-mailem."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems)
                                .shipments(tracked.get()),
                        "Czekamy na Twoją paczkę",
                        "Kurier odbierze paczkę spod adresu niżej. Gdzie jest teraz, sprawdzisz u przewoźnika."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems)
                                .shipments(failed.get()),
                        "Czekamy na Twoją paczkę",
                        "Sprawdzamy zamówienie kuriera — odezwiemy się, gdy będzie potwierdzone."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems),
                        "Czekamy na Twoją paczkę",
                        "Gdy paczka do nas dotrze, napiszemy e-mail."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.ItemsReceived),
                        "Twoja paczka do nas dotarła",
                        "Sprawdzamy produkty. O decyzji napiszemy e-mailem."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.Processing),
                        "Rozpatrujemy Twój zwrot",
                        "Damy znać e-mailem, gdy skończymy."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.Completed),
                        "Zwrot zakończony",
                        "Rozwiązanie każdego produktu znajdziesz niżej."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.Rejected)
                                .rejectionReason("Uszkodzenie mechaniczne."),
                        "Sklep odrzucił zgłoszenie zwrotu",
                        "Wyjaśnienie znajdziesz niżej. Jeśli się z nim nie zgadzasz, napisz do nas."),
                arguments((Supplier<ClientReturnRenderer>) () -> ClientReturnRenderer.approved().status(RMAStatus.Rejected),
                        "Sklep odrzucił zgłoszenie zwrotu",
                        "Jeśli masz pytania o tę decyzję, napisz do nas."));
    }

    @ParameterizedTest
    @MethodSource("states")
    void everyStateWithoutTheFormHasItsHeadlineAndLead(Supplier<ClientReturnRenderer> renderer, String headline, String lead) {
        // when
        String html = renderer.get().render();
        String english = renderer.get().locale(Locale.ENGLISH).render();

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">" + headline + "</h1>")
                .contains("<p class=\"cl-page-lead\">" + lead + "</p>")
                .doesNotContain("id=\"return-form\"")
                .doesNotContain("??");
        assertThat(english).doesNotContain("??");
        assertThat(html.split("<h1", -1)).hasSize(2);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);

    private static Shipment customerReturn() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setCarrier("dhl");
        return shipment;
    }

    private static Shipment trackedReturn() {
        Shipment shipment = customerReturn();
        shipment.setTrackingNo("00007731440201U");
        shipment.setTrackingUrl("https://tracking.example/00007731440201U");
        shipment.setShippedAt(LocalDateTime.of(2026, 10, 22, 10, 14));
        shipment.setPickup(ShipmentPickup.pending("p1", NOW, LocalDate.of(2026, 10, 23), LocalTime.of(9, 0), LocalTime.of(15, 0))
                .ordered("PICK1"));
        return shipment;
    }

    private static ShippingDetails fullAddress() {
        ShippingDetails details = ClientReturnRenderer.address();
        details.setPostalCode("00-950");
        return details;
    }

    @Test
    void waitingShowsTheParcelFirstWithTracking() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).savedAddress(fullAddress())
                .shipments(trackedReturn()).render();

        // then
        String parcel = card(html, "return-parcel");
        assertThat(parcel).contains("<h2 class=\"cl-card-title\" id=\"return-parcel-title\">Przesyłka</h2>")
                .contains("<p class=\"cl-list-title\">DHL</p>")
                .contains("<p class=\"cl-list-desc\">Twoja paczka zwrotna</p>")
                .contains("Numer: 00007731440201U")
                .contains("data-cl-copy=\"00007731440201U\"")
                .contains("aria-label=\"Kopiuj numer przesyłki\"")
                .contains("<a class=\"cl-button\" href=\"https://tracking.example/00007731440201U\" target=\"_blank\" rel=\"noopener\">")
                .contains("Śledź przesyłkę")
                .contains("<span class=\"cl-visually-hidden\"> (otwiera się w nowej karcie)</span>")
                .contains("nadana 22 paź, 10:14")
                .contains("odbiór pt. 23 paź, 9:00–15:00")
                .doesNotContain("cl-status");
        String content = main(html);
        assertThat(content).containsPattern("<div class=\"cl-stack\">\\s*<section[^>]*id=\"return-parcel\"");
        assertThat(content.indexOf("id=\"return-parcel\"")).isLessThan(content.indexOf("id=\"return-progress\""));
        assertThat(content.indexOf("id=\"return-progress\"")).isLessThan(content.indexOf("id=\"return-address\""));
        assertThat(content.indexOf("id=\"return-address\"")).isLessThan(content.indexOf("id=\"return-items\""));
        assertThat(content.indexOf("id=\"return-items\"")).isLessThan(content.indexOf("id=\"return-help\""));
    }

    @Test
    void twoShipmentsAreCountedAndTheStoreOneSaysWhoseItIs() {
        // given
        Shipment fromStore = new Shipment(ShipmentType.Courier);
        fromStore.setProvider("furgonetka");
        fromStore.setPickUpAddressId("warehouse");
        fromStore.setCarrier("dpd");
        fromStore.setDeliveredAt(LocalDateTime.of(2026, 10, 20, 14, 5));

        // when
        String parcel = card(ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems)
                .shipments(trackedReturn(), fromStore).render(), "return-parcel");

        // then
        assertThat(parcel).contains("id=\"return-parcel-title\">Przesyłki: 2</h2>")
                .contains("<p class=\"cl-list-desc\">Przesyłka od sklepu</p>")
                .contains("<span class=\"cl-status is-ok\">Dostarczona</span>")
                .contains("doręczona 20 paź, 14:05");
    }

    @Test
    void aShipmentWithoutCarrierIsNamedByItsTypeOrGenerically() {
        // given
        Shipment typed = new Shipment(ShipmentType.PickupPoint);
        Shipment untyped = new Shipment();
        untyped.setType(null);

        // when
        String parcel = card(ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems)
                .shipments(typed, untyped).render(), "return-parcel");

        // then
        assertThat(parcel).contains("<p class=\"cl-list-title\">Punkt odbioru</p>")
                .contains("<p class=\"cl-list-title\">Przesyłka</p>")
                .doesNotContain("??")
                .doesNotContain("null");
    }

    @Test
    void bookingShowsTheParcelBeingBooked() {
        // given
        Shipment booking = customerReturn();
        booking.setCreation(ShipmentCreationState.pending("cmd", NOW));

        // when
        String html = ClientReturnRenderer.approved().savedAddress(fullAddress()).shipments(booking).render();

        // then
        String parcel = card(html, "return-parcel");
        assertThat(parcel).contains("<p class=\"cl-list-title\">DHL <span class=\"cl-status is-info\">Zamawiamy kuriera</span></p>")
                .contains("Numer przesyłki i termin odbioru wyślemy e-mailem.")
                .doesNotContain("Numer:")
                .doesNotContain("Śledź przesyłkę");
        assertThat(main(html)).containsPattern("<div class=\"cl-stack\">\\s*<section[^>]*id=\"return-parcel\"");
    }

    @Test
    void checkingNeverShowsTheProvidersError() {
        // given
        Shipment refused = customerReturn();
        refused.setTrackingNo("MAYBE1");
        refused.setCreation(ShipmentCreationState.pending("cmd", NOW).failed("401 Unauthorized <script>"));

        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).shipments(refused).render();

        // then
        String parcel = card(html, "return-parcel");
        assertThat(parcel).contains("<span class=\"cl-status is-warn\">Sprawdzamy zamówienie kuriera</span>")
                .contains("Nie musisz nic robić — sklep sprawdza u przewoźnika, czy kurier jest zamówiony, i odezwie się do Ciebie.")
                .doesNotContain("MAYBE1");
        assertThat(html).doesNotContain("401").doesNotContain("Unauthorized");
    }

    @Test
    void parcelNoticeStandsInTheParcelCardOnce() {
        // given
        Shipment booking = customerReturn();
        booking.setCreation(ShipmentCreationState.pending("cmd", NOW));

        // when
        String html = ClientReturnRenderer.approved().savedAddress(fullAddress()).shipments(booking)
                .notice(ClientReturnNotice.ok(ClientReturnNotice.PARCEL, "Zamówiliśmy kuriera <b>.")).render();

        // then
        String parcel = card(html, "return-parcel");
        assertThat(parcel).containsPattern("<div class=\"cl-card-inset\">\\s*<div class=\"cl-alert is-ok\" role=\"status\" tabindex=\"-1\">")
                .contains("<p class=\"cl-alert-text\">Zamówiliśmy kuriera &lt;b&gt;.</p>");
        assertThat(parcel.indexOf("cl-alert")).isLessThan(parcel.indexOf("<ul class=\"cl-list\">"));
        assertThat(main(html).split("cl-alert-text", -1)).hasSize(2);
        assertThat(main(html)).containsPattern("<div class=\"cl-stack\">\\s*<section[^>]*id=\"return-parcel\"");
    }

    @Test
    void stagesMarkTheCurrentOne() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).render();

        // then
        String progress = card(html, "return-progress");
        assertThat(progress).contains("<h2 class=\"cl-card-title\" id=\"return-progress-title\">Postęp zwrotu</h2>")
                .contains("<ol class=\"cl-stages\" aria-label=\"Etapy zwrotu\">")
                .contains("<span class=\"cl-stage-name\">Przyjęte</span>")
                .contains("<span class=\"cl-stage-when\">21 paź</span>")
                .contains("<span class=\"cl-stage-name\">Czekamy na paczkę</span>")
                .contains("<span class=\"cl-stage-name\">Paczka dotarła</span>")
                .contains("<span class=\"cl-stage-name\">Rozpatrujemy</span>")
                .contains("<span class=\"cl-stage-name\">Zakończone</span>")
                .containsPattern("<li class=\"cl-stage is-current\" aria-current=\"step\">\\s*<span class=\"cl-stage-dot\" aria-hidden=\"true\">2</span>")
                .doesNotContain("cl-status");
        assertThat(progress.split("<li class=\"cl-stage", -1)).hasSize(6);
        assertThat(progress.split("aria-current=\"step\"", -1)).hasSize(2);
        assertThat(progress.split("ukończony: ", -1)).hasSize(2);
    }

    @Test
    void completedStagesAreAllDoneWithTheArrivalDate() {
        // given
        Shipment arrived = trackedReturn();
        arrived.setDeliveredAt(LocalDateTime.of(2026, 10, 23, 9, 30));

        // when
        String progress = card(ClientReturnRenderer.approved().status(RMAStatus.Completed).shipments(arrived).render(),
                "return-progress");

        // then
        assertThat(progress.split("ukończony: ", -1)).hasSize(6);
        assertThat(progress).doesNotContain("aria-current")
                .contains("<span class=\"cl-stage-when\">23 paź</span>");
    }

    @Test
    void addressIsMaskedAndHasNoNull() {
        // given
        ShippingDetails noName = fullAddress();
        noName.setName(null);

        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).savedAddress(fullAddress())
                .returnPackage("t-s").render();
        String withoutName = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).savedAddress(noName).render();

        // then
        String address = card(html, "return-address");
        assertThat(address).contains("<h2 class=\"cl-card-title\" id=\"return-address-title\">Odbiór paczki</h2>")
                .contains("<h3>Adres</h3>")
                .contains("<span>Jan*********</span>")
                .contains("<p>00-950 Warszawa, PL</p>")
                .contains("<h3>Paczka</h3>")
                .contains("<p>Karton S</p>")
                .contains("<p class=\"cl-muted\">25 × 15 × 10 cm · 1 kg</p>")
                .doesNotContain("Chcesz zmienić adres?");
        assertThat(main(html)).doesNotContain("Kowalski").doesNotContain("Marszałkowska").doesNotContain("601234567")
                .doesNotContain("jan.kowalski@poczta").doesNotContain("null");
        String bare = card(withoutName, "return-address");
        assertThat(bare).contains("Kow*****").doesNotContain("nul").doesNotContain("<h3>Paczka</h3>");
    }

    @Test
    void addressCardOnlyBeforeTheParcelArrives() {
        // when
        String storeBooks = ClientReturnRenderer.approved().savedAddress(fullAddress()).render();
        String arrived = ClientReturnRenderer.approved().status(RMAStatus.ItemsReceived).savedAddress(fullAddress()).render();
        String waitingWithoutAddress = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).render();

        // then
        assertThat(card(storeBooks, "return-address"))
                .contains("<p class=\"cl-help\">Chcesz zmienić adres? Napisz do nas, zanim kurier wyruszy.</p>");
        assertThat(arrived).doesNotContain("id=\"return-address\"").doesNotContain("00-950");
        assertThat(waitingWithoutAddress).doesNotContain("id=\"return-address\"");
    }

    @Test
    void rejectedShowsTheReasonAsTextWithoutStages() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.Rejected)
                .rejectionReason("Uszkodzenie <b>mechaniczne</b>.\nDruga linia.").render();

        // then
        String reason = card(html, "return-reason");
        assertThat(reason).contains("<h2 class=\"cl-card-title\" id=\"return-reason-title\">Powód odrzucenia</h2>")
                .contains("<blockquote class=\"cl-quote\">Uszkodzenie &lt;b&gt;mechaniczne&lt;/b&gt;.\nDruga linia.</blockquote>")
                .contains("<p class=\"cl-quote-source\">Wyjaśnienie od sklepu</p>");
        String content = main(html);
        assertThat(content).doesNotContain("cl-stages").doesNotContain("id=\"return-progress\"")
                .doesNotContain("<b>mechaniczne");
        assertThat(content).containsPattern("<div class=\"cl-stack\">\\s*<section[^>]*id=\"return-reason\"");
        assertThat(card(html, "return-items")).doesNotContain("cl-status");
    }

    @Test
    void rejectedWithoutReasonSaysSo() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.Rejected).rejectionReason("   ").render();

        // then
        assertThat(card(html, "return-reason")).contains("<p class=\"cl-note\">Sklep nie podał powodu. Jeśli chcesz go poznać albo nie zgadzasz się z decyzją, napisz do nas — dane kontaktowe są niżej.</p>")
                .doesNotContain("cl-quote");
    }

    @Test
    void reasonIsNotShownOutsideRejected() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.Completed).rejectionReason("Stary powód.").render();

        // then
        assertThat(html).doesNotContain("id=\"return-reason\"").doesNotContain("Stary powód.").doesNotContain("cl-quote");
    }

    @Test
    void completedShowsResolutions() {
        // given
        RMAItem card = ClientReturnRenderer.item("Karta graficzna", 1, RMAResolutionType.Repair);
        card.setActualResolution(RMAResolutionType.Replacement);
        card.setStatus(RMAItemStatus.ReturnedToClient);
        RMAItem memory = ClientReturnRenderer.item("Pamięć", 2, RMAResolutionType.Return);
        memory.setStatus(RMAItemStatus.New);

        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.Completed).items(card, memory).render();

        // then
        String items = card(html, "return-items");
        assertThat(items).contains("<h2 class=\"cl-card-title\" id=\"return-items-title\">Produkty w zwrocie: 2</h2>")
                .contains("<ul class=\"cl-list is-items\">")
                .contains("<p class=\"cl-list-title\">Karta graficzna</p>")
                .contains("<p class=\"cl-list-desc\">1 szt. · Oczekujesz: Naprawa · Rozwiązanie: Wymiana</p>")
                .contains("<p class=\"cl-list-desc\">2 szt. · Oczekujesz: Zwrot</p>")
                .contains("<p class=\"cl-list-desc cl-muted\">„Nie działa.”</p>")
                .contains("<span class=\"cl-status is-ok\">Odesłany do Ciebie</span>");
        assertThat(items.split("cl-status", -1)).hasSize(2);
        String content = main(html);
        assertThat(content.indexOf("id=\"return-progress\"")).isLessThan(content.indexOf("id=\"return-items\""));
    }

    @Test
    void itemsWithHtmlInNamesAreText() {
        // given
        RMAItem item = ClientReturnRenderer.item("Dysk <img src=x onerror=alert(1)>", 1, RMAResolutionType.Return);
        item.setReason("<b>głośny</b>");

        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.Processing).items(item).render();

        // then
        assertThat(card(html, "return-items")).contains("Dysk &lt;img src=x onerror=alert(1)&gt;")
                .contains("„&lt;b&gt;głośny&lt;/b&gt;”")
                .contains("Produkty w zwrocie: 1");
        assertThat(html).doesNotContain("<img src=x").doesNotContain("<b>głośny");
    }

    @Test
    void contactQuotesTheReturnNumber() {
        // when
        String html = ClientReturnRenderer.approved().status(RMAStatus.Processing).contactPhone("+48 22 390 45 10").render();
        String noContact = ClientReturnRenderer.approved().status(RMAStatus.Processing).contactEmail(null).render();

        // then
        String help = card(html, "return-help");
        assertThat(help).contains("<h2 class=\"cl-card-title\" id=\"return-help-title\">Masz pytanie?</h2>")
                .contains("Napisz do nas:")
                .contains("<a href=\"mailto:biuro@novatech.pl\">biuro@novatech.pl</a>")
                .contains("albo zadzwoń:")
                .contains("<a href=\"tel:+48223904510\">+48 22 390 45 10</a>")
                .contains("i podaj numer zwrotu")
                .contains("<strong>3f9c2a71</strong>.");
        assertThat(noContact).doesNotContain("id=\"return-help\"");
        assertThat(main(html).indexOf("id=\"return-help\"")).isGreaterThan(main(html).indexOf("id=\"return-items\""));
    }

    @Test
    void noShipmentCardWithoutRows() {
        // given
        Shipment failedFromStore = new Shipment(ShipmentType.Courier);
        failedFromStore.setProvider("furgonetka");
        failedFromStore.setPickUpAddressId("warehouse");
        failedFromStore.setCreation(ShipmentCreationState.pending("cmd", NOW).failed("refused"));

        // when
        String none = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).render();
        String onlyFailed = ClientReturnRenderer.approved().status(RMAStatus.Completed).shipments(failedFromStore).render();

        // then
        assertThat(none).doesNotContain("id=\"return-parcel\"");
        assertThat(onlyFailed).doesNotContain("id=\"return-parcel\"").doesNotContain("refused");
    }

    @Test
    void englishWaitingAndRejected() {
        // when
        String waiting = ClientReturnRenderer.approved().status(RMAStatus.WaitingForItems).savedAddress(fullAddress())
                .returnPackage("t-s").shipments(trackedReturn()).locale(Locale.ENGLISH).render();
        String rejected = ClientReturnRenderer.approved().status(RMAStatus.Rejected).rejectionReason("Mechanical damage.")
                .locale(Locale.ENGLISH).render();

        // then
        assertThat(waiting).contains("id=\"return-parcel-title\">Shipment</h2>")
                .contains("Your return parcel")
                .contains("Number: 00007731440201U")
                .contains("Track shipment")
                .contains("(opens in a new tab)")
                .contains("dispatched 22 Oct, 10:14")
                .contains("pickup Fri 23 Oct, 9:00–15:00")
                .contains("Return progress")
                .contains("Waiting for the parcel")
                .contains("Parcel pickup")
                .contains("Products in the return: 2")
                .contains("1 pc · You expect: Repair")
                .contains("and quote the return number")
                .doesNotContain("??");
        assertThat(rejected).contains("Reason for rejection")
                .contains("<blockquote class=\"cl-quote\">Mechanical damage.</blockquote>")
                .contains("Explanation from the store")
                .doesNotContain("cl-stages")
                .doesNotContain("??");
    }

    @Test
    void noInlineStylesInTheContent() throws IOException {
        // when
        String html = ClientReturnRenderer.approved().refused(ClientReturnRenderer.address(), "t-s",
                Map.of("shippingDetails.name", "client.return.form.name.required"))
                .notice(ClientReturnNotice.bad(ClientReturnNotice.FORM, "Nie.")).render();

        // then
        assertThat(main(html)).doesNotContain("style=");
        for (Path source : templateSources()) {
            assertThat(Files.readString(source)).as(source.toString())
                    .doesNotContain("th:style")
                    .doesNotContain("style=")
                    .doesNotContain("th:utext");
        }
    }

    @Test
    void pageUsesThePublicFrame() {
        // when
        String html = ClientReturnRenderer.approved().render();

        // then
        assertThat(html).contains("<body class=\"cl-public\"")
                .contains("<main id=\"clContent\" class=\"cl-page cl-public-main is-form\"")
                .contains("<div class=\"cl-client-return\">")
                .contains("<header class=\"cl-page-header\">")
                .contains("/js/submit-once.js")
                .contains("/js/copy-field.js")
                .contains("<footer class=\"cl-public-foot\">")
                .doesNotContain("navbar");
    }

    static List<Path> templateSources() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        try (Stream<Path> fragments = Files.list(templates.resolve("client-return"))) {
            return Stream.concat(Stream.of(templates.resolve("client-return.html")),
                    fragments.filter(path -> path.toString().endsWith(".html")).sorted()).toList();
        }
    }

    /** The section with the id, up to its end. */
    private static String card(String html, String id) {
        int start = html.lastIndexOf("<section", html.indexOf("id=\"" + id + "\""));
        assertThat(start).as("card %s", id).isNotNegative();
        return html.substring(start, html.indexOf("</section>", start));
    }
}
