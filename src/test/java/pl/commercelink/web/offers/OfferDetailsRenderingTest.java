package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.web.offers.BasketViewPage.Market;
import pl.commercelink.web.offers.OfferDetailsPage.*;
import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.ItemMargin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.settings.SettingsTemplateRenderer.render;

class OfferDetailsRenderingTest {

    private static final String PAGE = "<section th:replace=\"~{offers/details :: page}\"></section>";
    private static final String ID = "4b1e9c55-8f3a-4d7a-9c55-1e0b6a2f7d10";
    private static final String NBSP = "\u00A0";
    private static final AddressBlock BILLING = new AddressBlock("Marta Zielińska", "Biuro Zielińska", "6762468313",
            "ul. Wielicka 28/4", "30-552 Kraków", "Polska", "biuro@x.pl", "+48 601");

    private static Item laptop(int index) {
        return new Item(index, "id-" + index, "cat-1", "Laptop Lenovo T14", "Laptopy", "21ML003RPB", 3, "6899.00",
                "5810.00", 6899, 5810, false, null, false, false, 5, false, ItemMargin.of(6899, 5810, 1.23),
                new Market(false, "5" + NBSP + "810,00", "5" + NBSP + "949,00", 5, 41), "Action", "Elko, AB", 2, 7);
    }

    private static Item dock(int index) {
        return new Item(index, "id-" + index, "cat-1", "Stacja dokująca", "Akcesoria", "40AY0090EU", 3, "799.00",
                "0.00", 799, 0, false, null, false, false, null, false, ItemMargin.of(799, 0, 1.23),
                new Market(false, "611,00", "642,00", 4, 22), "Action", "", 1, 1);
    }

    private static Item monitor(int index, String name, boolean selected) {
        return new Item(index, "id-" + index, "cat-1", name, "Monitory", "MON-" + index, 3, "1749.00", "1402.00",
                1749, 1402, false, "Monitor", selected, false, null, false, ItemMargin.of(1749, 1402, 1.23),
                Market.none(), null, null, 0, 0);
    }

    private static Item service(int index) {
        return new Item(index, "id-" + index, null, "Konfiguracja systemu", null, "SRV-1", 3, "199.00", "0.00", 199,
                0, false, null, false, true, null, false, null, null, null, null, 0, 0);
    }

    private static Item withoutQuantity(int index) {
        return new Item(index, "id-" + index, "cat-1", "Mysz", "Myszy", "910-006559", 0, "449.00", "338.00", 449,
                338, true, null, false, false, null, true, ItemMargin.of(449, 338, 1.23), Market.none(), null, null, 0, 0);
    }

    private static Summary summary(boolean removed) {
        return new Summary("33" + NBSP + "882,00", removed ? null : "Kurier InPost", removed, removed ? "0,00" : "19,99",
                removed ? 0 : 19.99, "33" + NBSP + "901,99", "27" + NBSP + "562,59", "26" + NBSP + "655,00",
                "7" + NBSP + "246,99", "5" + NBSP + "891,86", true);
    }

    private static Customer customer(boolean billing, boolean contact) {
        return contact
                ? new Customer("Marta", "Zielińska", "Biuro Zielińska", "biuro@x.pl", "+48 601", true,
                billing ? BILLING : null, null, true)
                : new Customer(null, null, null, null, null, false, null, null, false);
    }

    private static Terms terms() {
        return new Terms("24.10.2026, 23:59", "2026-10-24T23:59", "courier",
                List.of(new DeliveryChoice("courier", "Kurier InPost (19,99 PLN)")), true,
                "store.fulfilment.type.WarehouseFulfilment", null);
    }

    private static OfferDetailsPage offer(List<Line> lines, Summary summary, Customer customer, boolean proforma) {
        return new OfferDetailsPage(ID, "store-1", "4b1e9c55", false, "Stanowiska dla biura", "/dashboard/offers",
                "offers.details.back.offers", new Pill("offers.list.pill.active", null, "is-ok"),
                new Meta(customer.contactFilled() ? "Marta Zielińska · Biuro Zielińska" : null, "do 24.10 (5 dni)", "12.10.2026, 14:05", "Anna Kowalska",
                        "33" + NBSP + "901,99", 4),
                lines, List.of(new StoreCategories.Group("Komputery", List.of("Laptopy", "Akcesoria"))), List.of(),
                summary, terms(), customer, "Zadzwonić w poniedziałek", "https://app.test/store/s/client/offer/" + ID,
                proforma, customer.contactFilled(), "Stanowiska dla biura", "WarehouseFulfilment");
    }

    private static OfferDetailsPage fullOffer() {
        return offer(List.of(laptop(0), dock(1), new GroupHead("Monitor", 2), monitor(2, "Monitor Dell", true),
                        monitor(3, "Monitor LG", false), service(4)),
                summary(false), customer(true, true), true);
    }

    private static OfferDetailsPage template() {
        return new OfferDetailsPage(ID, "store-1", "4b1e9c55", true, "Stanowisko biurowe", "/dashboard/offers?segment=templates",
                "offers.details.back.templates", new Pill("offers.details.pill.template", null, "is-neutral"),
                new Meta(null, null, "02.09.2026, 10:00", "Anna Kowalska", "7" + NBSP + "698,00", 2),
                List.of(laptop(0), service(1)), List.of(), List.of(),
                new Summary("7" + NBSP + "698,00", null, false, "0,00", 0, "7" + NBSP + "698,00", "6" + NBSP + "258,54",
                        "5" + NBSP + "810,00", "1" + NBSP + "888,00", "1" + NBSP + "535,28", false),
                new Terms(null, null, null, List.of(), true, "store.fulfilment.type.WarehouseFulfilment", null),
                customer(false, false), null, null, false, false, "Stanowisko biurowe", "WarehouseFulfilment");
    }

    private static Map<String, Object> model(OfferDetailsPage page) {
        Map<String, Object> model = new HashMap<>();
        model.put("page", page);
        model.put("termsForm", new OfferTermsForm());
        model.put("clientForm", new ContactDetails());
        model.put("noteForm", new OfferNoteForm());
        model.put("errors", Map.of());
        return model;
    }

    private static String html(OfferDetailsPage page) {
        return render(PAGE, model(page));
    }

    private static String header(String html) {
        return html.substring(html.indexOf("class=\"cl-page-header\""), html.indexOf("id=\"offer-items\""));
    }

    private static String row(String html, int index) {
        int start = html.indexOf("data-index=\"" + index + "\"");
        return html.substring(start, html.indexOf("</tr>", start));
    }

    private static int count(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void rendersRecordHeaderWithValidityAndOneMainAction() {
        // when
        String html = html(fullOffer());

        // then
        String header = header(html);
        assertThat(header).contains("<h1 class=\"cl-page-title\">Stanowiska dla biura</h1>")
                .contains("class=\"cl-status is-ok\"").contains("Ważna")
                .contains("class=\"cl-back\" href=\"/dashboard/offers\"")
                .contains("Marta Zielińska · Biuro Zielińska").contains("do 24.10 (5 dni)")
                .contains("utworzona 12.10.2026, 14:05 przez Anna Kowalska").contains("33" + NBSP + "901,99 PLN")
                .contains("pozycje: 4")
                .contains("href=\"/dashboard/offer/" + ID + "/order\"").contains("data-cl-confirm-tone=\"primary\"")
                .contains("Utworzyć zamówienie z oferty „Stanowiska dla biura”?");
        assertThat(count(header, "cl-button is-primary")).isEqualTo(1);
        assertThat(header.substring(header.indexOf("cl-button is-primary"))).contains("Utwórz zamówienie");
        assertThat(html.split("<h1", -1)).hasSize(2);
    }

    @Test
    void itemsFormCarriesRecordNameAndFulfilment() {
        // when
        String html = html(fullOffer());

        // then
        String form = html.substring(html.indexOf("<form id=\"offer-items\""));
        assertThat(form).startsWith("<form id=\"offer-items\"")
                .contains("action=\"/dashboard/offer/" + ID + "\"")
                .contains("<input type=\"hidden\" name=\"name\" value=\"Stanowiska dla biura\"")
                .contains("<input type=\"hidden\" name=\"fulfilmentType\" value=\"WarehouseFulfilment\"")
                .contains("<input type=\"hidden\" name=\"storeId\" value=\"store-1\"")
                .contains("<input type=\"hidden\" name=\"type\" value=\"Offer\"")
                .contains("<input type=\"hidden\" name=\"basketId\" value=\"" + ID + "\"");
        assertThat(form.substring(0, form.indexOf(">"))).contains("data-cl-dirty-form");
        assertThat(html).contains("formaction=\"/dashboard/offer/" + ID + "/saveAsTemplate\"")
                .contains("form=\"offer-items\"");
    }

    @Test
    void itemRowHasOnlyQtyAndPriceInputs() {
        // when
        String row = row(html(fullOffer()), 0);

        // then
        Matcher visible = Pattern.compile("<input (?![^>]*type=\"hidden\")[^>]*>").matcher(row);
        int inputs = 0;
        while (visible.find()) {
            inputs++;
            assertThat(visible.group()).containsAnyOf("name=\"basketItems[0].qty\"", "name=\"basketItems[0].unitPrice\"");
        }
        assertThat(inputs).isEqualTo(2);
        assertThat(row).contains("data-price=\"6899.0\"").contains("data-cost=\"5810.0\"").contains("data-qty=\"3\"")
                .contains("data-selected=\"false\"").contains("data-service=\"false\"")
                .contains("name=\"basketItems[0].mfn\" value=\"21ML003RPB\"")
                .contains("name=\"basketItems[0].category\" value=\"Laptopy\"")
                .contains("name=\"basketItems[0].unitCost\" value=\"5810.00\"")
                .contains("name=\"basketItems[0].consolidated\" value=\"false\"")
                .contains("name=\"basketItems[0].estimatedDeliveryDays\" value=\"5\"")
                .contains("name=\"basketItems[0].variantGroupId\" value=\"\"")
                .contains("koszt 5" + NBSP + "810,00").contains("marża 15,8 %").contains("20" + NBSP + "697,00")
                .contains("href=\"/dashboard/offer/" + ID + "/items/0\"").contains("data-cl-dialog-open=\"item-edit-dialog\"")
                .contains("Rynek: 5" + NBSP + "810,00 PLN → 5" + NBSP + "949,00 PLN");
    }

    @Test
    void zeroCostShowsWarningLine() {
        // when
        String row = row(html(fullOffer()), 1);

        // then
        assertThat(row).contains("<span class=\"cl-line-hint is-warn\"").contains("brak kosztu").contains("marża: ?");
    }

    @Test
    void incompleteItemWarnsItWillBeRemoved() {
        // given
        OfferDetailsPage page = offer(List.of(laptop(0), withoutQuantity(1)), summary(false), customer(true, true), true);

        // when
        String html = html(page);

        // then
        assertThat(row(html, 1)).contains("Pozycja zniknie przy zapisie").doesNotContain("data-cl-line-warn hidden")
                .contains("Połącz na FV");
        assertThat(row(html, 0)).contains("data-cl-line-warn hidden");
    }

    @Test
    void variantGroupHeadAndSelectedMarker() {
        // when
        String html = html(fullOffer());

        // then
        assertThat(html).contains("data-cl-line-group").contains("data-cl-group-name").contains("value=\"Monitor\"")
                .contains("klient wybiera 1 z 2").contains("Rozgrupuj");
        // every row carries the marker for offer-lines.js, shown only on the chosen variant
        assertThat(count(html, "data-cl-mark-selected>wybrany — w sumie")).isEqualTo(1);
        assertThat(row(html, 2)).contains("data-cl-mark-selected>wybrany — w sumie").contains("data-group=\"Monitor\"")
                .contains("data-selected=\"true\"");
        assertThat(row(html, 3)).contains("poza sumą").contains("data-selected=\"false\"")
                .contains("data-cl-mark-selected hidden=\"hidden\">");
        assertThat(html).contains("class=\"cl-line is-variant is-unpicked\"");
        assertThat(row(html, 4)).contains("Usługa").doesNotContain("cl-line-hint");
    }

    @Test
    void summaryShowsTodaysFigures() {
        // when
        String html = html(fullOffer());

        // then
        String summary = html.substring(html.indexOf("id=\"summary\""), html.indexOf("id=\"offer-terms-card\""));
        assertThat(summary).contains("Dostawa · Kurier InPost").contains("data-cl-delivery-price=\"19.99\"")
                .contains("Razem dla klienta").contains("Nasz koszt").contains("Zysk brutto").contains("Zysk netto")
                .contains("data-cl-sum=\"items\"").contains("data-cl-sum=\"total\"").contains("data-cl-sum=\"net\"")
                .contains("data-cl-sum=\"cost\"").contains("data-cl-sum=\"profitGross\"").contains("data-cl-sum=\"profitNet\"")
                .contains("33" + NBSP + "901,99 PLN").contains("26" + NBSP + "655,00 PLN")
                .contains("7" + NBSP + "246,99 PLN").contains("5" + NBSP + "891,86 PLN")
                .contains("Sumy liczone z domyślnych pozycji grup")
                .contains("Wszystkie zmiany zapisane").contains("data-cl-dirty-status")
                .contains("data-cl-save-items").contains("data-cl-discard").contains("Zapisz pozycje");
        assertThat(html).contains("class=\"cl-summary-peek is-action\" data-cl-peek");
    }

    @Test
    void proformaDisabledWithReasonWithoutBilling() {
        // given
        OfferDetailsPage withoutBilling = offer(List.of(laptop(0)), summary(false), customer(false, true), false);

        // when
        String html = html(withoutBilling);
        String withBilling = html(fullOffer());

        // then
        String header = header(html);
        String proforma = header.substring(header.indexOf("Faktura proforma") - 300, header.indexOf("Faktura proforma") + 300);
        assertThat(proforma).contains("aria-disabled=\"true\"").contains("cl-menu-reason")
                .contains("Klient nie wysłał jeszcze danych do faktury");
        assertThat(header).doesNotContain("/createProformaInvoice");
        assertThat(html).contains("Jeszcze nie wysłane");
        assertThat(header(withBilling)).contains("formaction=\"/dashboard/offer/" + ID + "/createProformaInvoice\"");
    }

    @Test
    void templateHasOneActionAndNoClientCards() {
        // when
        String html = html(template());

        // then
        String header = header(html);
        assertThat(header).contains("Utwórz ofertę z szablonu").contains("class=\"cl-status is-neutral\"")
                .contains("Szablon").contains("href=\"/dashboard/offer/new?intent=template&amp;sourceId=" + ID + "\"")
                .contains("utworzony 02.09.2026, 10:00 przez Anna Kowalska")
                .doesNotContain("Więcej").doesNotContain("cl-menu");
        assertThat(count(header, "cl-button is-primary")).isEqualTo(1);
        assertThat(html).doesNotContain("offer-client-card").doesNotContain("offer-client-dialog")
                .doesNotContain("Dane do faktury").doesNotContain("Ważna do")
                .contains("<input type=\"hidden\" name=\"type\" value=\"OfferTemplate\"");
    }

    @Test
    void removedDeliveryIsWarned() {
        // given
        OfferDetailsPage page = offer(List.of(laptop(0)), summary(true), customer(true, true), true);

        // when
        String html = html(page);

        // then
        assertThat(html).contains("usunięty sposób dostawy").contains("data-cl-delivery-price=\"0.0\"");
    }

    @Test
    void contactlessOfferHidesCopyWithContact() {
        // given
        OfferDetailsPage page = offer(List.of(laptop(0)), summary(false), customer(false, false), false);

        // when
        String html = html(page);

        // then
        assertThat(html).doesNotContain("Duplikuj z danymi klienta").contains("Duplikuj")
                .contains("Brak danych kontaktowych klienta").contains("bez danych klienta");
        assertThat(html(fullOffer())).contains("formaction=\"/dashboard/offer/" + ID + "/copy?withContact=true\"");
    }

    @Test
    void lastItemCannotBeRemovedAndOthersPostToRemoveItem() {
        // when
        String single = html(offer(List.of(laptop(0)), summary(false), customer(true, true), true));
        String many = html(fullOffer());

        // then
        assertThat(row(single, 0)).contains("Oferta musi mieć co najmniej jedną pozycję").contains("aria-disabled=\"true\"")
                .doesNotContain("/remove-item/");
        assertThat(row(many, 1)).contains("formaction=\"/dashboard/offer/" + ID + "/remove-item/1\"")
                .contains("form=\"offer-action-form\"");
    }

    @Test
    void cardDialogsOpenFromLinksAndWithoutJavaScriptFromTheOpenParameter() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("open", "terms");

        // when
        String html = render(PAGE, model);

        // then
        assertThat(html).contains("href=\"/dashboard/offer/" + ID + "?open=terms#offer-terms-dialog\"")
                .contains("href=\"/dashboard/offer/" + ID + "?open=client#offer-client-dialog\"")
                .contains("href=\"/dashboard/offer/" + ID + "?open=note#offer-note-dialog\"")
                .containsPattern("<dialog class=\"cl-dialog is-form\" id=\"offer-terms-dialog\"[^>]* open")
                .containsPattern("<dialog class=\"cl-dialog is-form\" id=\"offer-client-dialog\"[^>]*>")
                .contains("id=\"offer-note-dialog\"").contains("id=\"item-edit-dialog\"")
                .contains("Zapisz warunki").contains("Zapisz dane klienta").contains("Zapisz notatkę")
                .contains("Śledzenie marketingowe").contains("id=\"item-add-dialog\"");
        assertThat(html.substring(html.indexOf("id=\"offer-client-dialog\"") - 80, html.indexOf("id=\"offer-client-dialog\"") + 120))
                .doesNotContain(" open");
    }

    @Test
    void savedDialogFormCarriesTheRefreshedCardsAndHeader() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("saved", true);

        // when
        String html = render("<form th:replace=\"~{offers/details :: termsForm}\"></form>", model);

        // then
        assertThat(html).startsWith("<form id=\"offer-terms-form\"").contains("data-cl-async")
                .contains("<template data-cl-offer-refresh>").contains("id=\"offer-header\"")
                .contains("id=\"offer-terms-card\"").contains("id=\"offer-client-card\"").contains("id=\"offer-note-card\"")
                .contains("data-cl-delivery-price=\"19.99\"");
        assertThat(render("<form th:replace=\"~{offers/details :: noteForm}\"></form>", model(fullOffer())))
                .doesNotContain("data-cl-offer-refresh");
    }

    @Test
    void rejectedTermsShowTheErrorAtTheField() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("errors", Map.of("name", "offers.name.required"));

        // when
        String html = render("<form th:replace=\"~{offers/details :: termsForm}\"></form>", model);

        // then
        assertThat(html).contains("data-cl-error-summary").contains("Nazwa jest wymagana.").contains("aria-invalid=\"true\"");
    }

    @Test
    void itemPageEditsOneItemWithoutJavaScript() {
        // given
        Map<String, Object> model = model(fullOffer());
        OfferItemForm form = new OfferItemForm();
        form.setName("Stacja dokująca");
        form.setCategory("Spoza katalogu");
        form.setMfn("");
        model.put("form", form);
        model.put("errors", Map.of("mfn", "offers.details.item.mfn.required"));
        model.put("index", 1);
        model.put("formAction", "/dashboard/offer/" + ID + "/items/1");
        model.put("pageHref", "/dashboard/offer/" + ID);

        // when
        String html = render("<section th:replace=\"~{offers/item-edit :: page}\"></section>", model);

        // then
        assertThat(html).contains("action=\"/dashboard/offer/" + ID + "/items/1\"").contains("Edytuj pozycję")
                .contains("value=\"Stacja dokująca\"").contains("<option value=\"Spoza katalogu\" selected")
                .contains("Podaj kod producenta.").contains("Zapisz pozycję").contains("Rynek: 611,00 PLN → 642,00 PLN")
                .contains("href=\"/dashboard/offer/" + ID + "\"");
    }

    @Test
    void noBulmaPrimaryNoInlineStyles() {
        // when
        String offer = html(fullOffer());
        String template = html(template());

        // then
        for (String html : List.of(offer, template)) {
            assertThat(html).doesNotContain("style=").doesNotContain("onclick=").doesNotContain("??");
            Matcher classes = Pattern.compile("class=\"([^\"]*\\bis-primary\\b[^\"]*)\"").matcher(html);
            while (classes.find()) {
                assertThat(classes.group(1)).contains("cl-button");
            }
        }
    }

    @Test
    void notesOutsideFieldsUseClassesThatSizeThem() {
        // given — a bare cl-help is sized only inside a field, so sentences of a dialog or a card use their own classes
        String offer = html(fullOffer());
        String removed = html(offer(List.of(laptop(0)), summary(true), customer(true, true), true));

        // then
        assertThat(offer).contains("<p class=\"cl-dialog-effect\">Zapisze tylko te pola.")
                .contains("<p class=\"cl-dialog-effect\">Zmiana trafi do oferty po")
                .containsPattern("<p class=\"cl-dialog-effect\"><i class=\"fas fa-eye-slash\" aria-hidden=\"true\"></i>\\s*<span>Klient nie widzi tych danych");
        assertThat(removed).contains("<p class=\"cl-card-desc is-warn cl-card-foot-note\">Tego sposobu dostawy nie ma już");
    }
}
