package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.products.StoreCategories;
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
    private static final Long VERSION = 7L;
    private static final AddressBlock BILLING = new AddressBlock("Marta Zielińska", "Biuro Zielińska", "6762468313",
            "ul. Wielicka 28/4", "30-552 Kraków", "Polska", "biuro@x.pl", "+48 601");

    private static Item laptop(int index) {
        return new Item(index, "id-" + index, "cat-1", "Laptop Lenovo T14", "Laptopy", "21ML003RPB", 3, "6899.00",
                "5810.00", 6899, 5810, 5810, false, 5949, false, null, false, false, 5, false, ItemMargin.of(6899, 5810, 1.23),
                new Market(false, "5" + NBSP + "810,00", "5" + NBSP + "949,00", 5, 41), "Action", "Elko, AB", 2, 7);
    }

    private static Item dock(int index) {
        return new Item(index, "id-" + index, "cat-1", "Stacja dokująca", "Akcesoria", "40AY0090EU", 3, "799.00",
                "0.00", 799, 0, 0, false, 642, false, null, false, false, null, false, ItemMargin.of(799, 0, 1.23),
                new Market(false, "611,00", "642,00", 4, 22), "Action", "", 1, 1);
    }

    private static Item estimatedDock(int index) {
        return new Item(index, "id-" + index, "cat-1", "Stacja dokująca", "Akcesoria", "40AY0090EU", 3, "799.00",
                "0.00", 799, 0, 1721.4, true, 1721.4, false, null, false, false, null, false, ItemMargin.of(799, 1721.4, 1.23),
                new Market(false, "611,00", "1" + NBSP + "721,40", 4, 22), "Action", "", 1, 1);
    }

    private static Item monitor(int index, String name, boolean selected) {
        return new Item(index, "id-" + index, "cat-1", name, "Monitory", "MON-" + index, 3, "1749.00", "1402.00",
                1749, 1402, 1402, false, 0, false, "Monitor", selected, false, null, false, ItemMargin.of(1749, 1402, 1.23),
                Market.none(), null, null, 0, 0);
    }

    private static Item service(int index) {
        return new Item(index, "id-" + index, null, "Konfiguracja systemu", null, "SRV-1", 3, "199.00", "0.00", 199,
                0, 0, false, 0, false, null, false, true, null, false, null, null, null, null, 0, 0);
    }

    private static Item withoutQuantity(int index) {
        return new Item(index, "id-" + index, "cat-1", "Mysz", "Myszy", "910-006559", 0, "449.00", "338.00", 449,
                338, 338, false, 0, true, null, false, false, null, true, ItemMargin.of(449, 338, 1.23), Market.none(), null, null, 0, 0);
    }

    private static Summary summary(boolean removed) {
        return new Summary("33" + NBSP + "882,00", removed ? null : "Kurier InPost", removed, removed ? "0,00" : "19,99",
                removed ? 0 : 19.99, "33" + NBSP + "901,99", "27" + NBSP + "562,59", "26" + NBSP + "655,00",
                "7" + NBSP + "246,99", "5" + NBSP + "891,86", true, false);
    }

    private static Customer customer(boolean billing, boolean contact) {
        return contact
                ? new Customer("Marta", "Zielińska", "Biuro Zielińska", "biuro@x.pl", "+48 601", true,
                billing ? BILLING : null, null, true)
                : new Customer(null, null, null, null, null, false, null, null, false);
    }

    private static Terms terms() {
        return new Terms("24.10.2026, 23:59 (5 dni)", "2026-10-24T23:59", "courier",
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
                proforma, customer.contactFilled(), "Stanowiska dla biura", "WarehouseFulfilment", VERSION);
    }

    static OfferDetailsPage fullOffer() {
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
                        "5" + NBSP + "810,00", "1" + NBSP + "888,00", "1" + NBSP + "535,28", false, false),
                new Terms(null, null, null, List.of(), true, "store.fulfilment.type.WarehouseFulfilment", null),
                customer(false, false), null, null, false, false, "Stanowisko biurowe", "WarehouseFulfilment", VERSION);
    }

    static Map<String, Object> model(OfferDetailsPage page) {
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

    @Test
    void refusedClientPointsEachFieldOnlyAtWhatShows() {
        // given: a name but neither an e-mail nor a phone, refused by the server
        Map<String, Object> model = model(fullOffer());
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        model.put("clientForm", contact);
        model.put("errors", Map.of("contact", "offers.new.contact.missing"));

        // when
        String refused = render("<form th:replace=\"~{offers/details :: clientForm}\"></form>", model);
        String clean = render("<form th:replace=\"~{offers/details :: clientForm}\"></form>", model(fullOffer()));

        // then
        assertThat(field(refused, "contact-email")).contains("aria-describedby=\"offer-contact-channel-error\"");
        assertThat(field(refused, "contact-phone")).contains("aria-describedby=\"offer-contact-channel-error\"");
        assertThat(field(refused, "contact-name")).doesNotContain("aria-describedby");
        assertThat(field(refused, "contact-company")).doesNotContain("aria-describedby");
        assertThat(field(clean, "contact-email")).contains("aria-describedby=\"offer-contact-rule\"");
        assertThat(field(clean, "contact-name")).contains("aria-describedby=\"offer-contact-rule\"");
    }

    @Test
    void peekFollowsTheColumnsSoTheStickySideColumnEndsAboveIt() {
        // given
        OfferDetailsPage page = fullOffer();

        // when
        String html = html(page);

        // then
        String form = html.substring(html.indexOf("id=\"offer-items\""), html.indexOf("id=\"offer-action-form\""));
        assertThat(form.substring(0, form.indexOf('>'))).doesNotContain("cl-layout-aside");
        assertThat(form).contains("<div class=\"cl-layout-aside\">");
        assertThat(form.indexOf("cl-summary-peek is-action")).isGreaterThan(form.lastIndexOf("</aside>"));
        assertThat(form.substring(form.lastIndexOf("</aside>"), form.indexOf("cl-summary-peek is-action")))
                .contains("</div>");
    }

    private static String field(String html, String id) {
        int start = html.indexOf("id=\"" + id + "\"");
        return html.substring(html.lastIndexOf("<input", start), html.indexOf(">", start));
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
                // the list's count leaves the variant outside the total out, and next to the card's count says so
                .contains("w sumie 4 z 5 pozycji")
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
                // the store and the record come from the session and the address, never from the form
                .doesNotContain("name=\"storeId\"").doesNotContain("name=\"type\"").doesNotContain("name=\"basketId\"");
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
                .contains("Koszt u dostawców: 5" + NBSP + "810,00 PLN → 5" + NBSP + "949,00 PLN (najniższy → mediana)");
    }

    @Test
    void zeroCostShowsWarningLine() {
        // when
        String row = row(html(fullOffer()), 1);

        // then
        assertThat(row).contains("<span class=\"cl-line-hint is-warn\"").contains("brak kosztu").contains("marża: ?");
    }

    @Test
    void anEstimatedCostReadsAsApproximate() {
        // given
        OfferDetailsPage estimated = offer(List.of(laptop(0), estimatedDock(1)), estimatedSummary(true), customer(true, true), true);

        // when
        String html = html(estimated);

        // then
        assertThat(row(html, 1)).contains("koszt ≈ 1" + NBSP + "721,40<").contains("marża ≈ ")
                .contains("data-tooltip=\"Szacowana marża:").contains("data-median-cost=\"1721.4\"")
                .contains("data-cost=\"0.0\"").doesNotContain("brak kosztu");
        assertThat(row(html, 0)).contains("data-median-cost=\"5949.0\"").doesNotContain("≈").doesNotContain("Szacowana marża");
        assertThat(row(html, 1)).contains("name=\"basketItems[1].unitCost\" value=\"0.00\"");
        assertThat(summaryCard(html)).contains("w tym szacowany koszt pozycji bez kosztu");
        assertThat(summaryCard(html)).containsPattern("<span[^>]*data-cl-sum-estimated[^>]*>").doesNotContainPattern("<span[^>]*\\bhidden\\b[^>]*data-cl-sum-estimated");
        assertThat(summaryCard(html(fullOffer()))).containsPattern("<span[^>]*\\bhidden\\b[^>]*>w tym szacowany koszt");
    }

    @Test
    void anItemWithoutAnyMedianHasNoMedianAttribute() {
        // when
        String row = row(html(fullOffer()), 4);

        // then
        assertThat(row).doesNotContain("data-median-cost");
    }

    private static Summary estimatedSummary(boolean estimated) {
        Summary base = summary(false);
        return new Summary(base.itemsTotal(), base.deliveryName(), false, base.deliveryPrice(), base.deliveryPriceValue(),
                base.total(), base.net(), base.cost(), base.profitGross(), base.profitNet(), base.variantsNote(), estimated);
    }

    private static String summaryCard(String html) {
        return html.substring(html.indexOf("id=\"summary\""), html.indexOf("id=\"offer-terms-card\""));
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
    void theTableShowsAServicesSectionOnlyWhenThereAreServices() {
        // given
        OfferDetailsPage withoutServices = offer(List.of(laptop(0), dock(1)), summary(false), customer(true, true), true);

        // when
        String html = html(fullOffer());
        String productsOnly = html(withoutServices);

        // then
        String products = html.substring(html.indexOf("data-cl-lines-section=\"products\""), html.indexOf("data-cl-lines-section=\"services\""));
        String services = html.substring(html.indexOf("data-cl-lines-section=\"services\""), html.indexOf("</tbody>", html.indexOf("data-cl-lines-section=\"services\"")));
        assertThat(products).contains("data-index=\"0\"").contains("data-index=\"3\"").doesNotContain("data-index=\"4\"")
                .contains("value=\"Monitor\"");
        assertThat(services).contains("<tr class=\"cl-table-group\" data-cl-section-head>")
                .contains("<th scope=\"rowgroup\" colspan=\"6\"><span class=\"cl-eyebrow\">Usługi</span></th>")
                .contains("data-index=\"4\"").contains("Usługa");
        assertThat(services.indexOf("data-cl-section-head")).isLessThan(services.indexOf("data-index=\"4\""));
        // both sections are always there, so a draft that brings the first service has its place
        assertThat(productsOnly).contains("data-cl-lines-section=\"products\"").contains("data-cl-lines-section=\"services\"")
                .doesNotContain("data-cl-section-head").doesNotContain("<span class=\"cl-eyebrow\">Usługi</span>");
    }

    @Test
    void everyRowMenuOffersSetAsChosenHiddenUntilTheScriptDecides() {
        // when
        String html = html(fullOffer());

        // then
        assertThat(row(html, 3)).contains("<li hidden data-cl-line-select-item>")
                .contains("data-cl-line-select").contains("Ustaw jako wybrany");
        assertThat(row(html, 0)).contains("data-cl-line-select-item").contains("data-cl-line-select");
        assertThat(html).contains("data-msg-line-selected=");
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
                .doesNotContain("Dane do faktury").doesNotContain("Ważna do");
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
    void theClientLinkIsCopiedOnlyFromTheMoreMenu() {
        // given
        String clientUrl = "https://app.test/store/s/client/offer/" + ID;

        // when
        String html = html(fullOffer());

        // then
        String copyAttribute = "data-cl-copy=\"" + clientUrl + "\"";
        int menuStart = html.indexOf("id=\"offer-more-menu\"");
        String menu = html.substring(menuStart, html.indexOf("</ul>", menuStart));
        assertThat(count(html, copyAttribute)).isEqualTo(1);
        assertThat(menu).contains(copyAttribute);
        assertThat(html).doesNotContain("Otwórz jak klient").doesNotContain("Link dla klienta")
                .doesNotContain("href=\"" + clientUrl + "\"");
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
    void onlyTheSummaryTotalIsAnnouncedWhileTyping() {
        // when
        String html = html(fullOffer());
        String summary = html.substring(html.indexOf("id=\"summary\""));

        // then: an output is a polite live region by default, so every recount would read all six; the total is said
        // by its own status once the typing stops (offer-lines.js)
        Matcher outputs = Pattern.compile("<output data-cl-sum=\"(\\w+)\"([^>]*)>").matcher(summary);
        int checked = 0;
        while (outputs.find()) {
            assertThat(outputs.group(2)).as(outputs.group(1)).contains("aria-live=\"off\"");
            checked++;
        }
        // the six of the summary and the total of the peek, which follows the side column
        assertThat(checked).isEqualTo(7);
        assertThat(summary).contains("role=\"status\" data-cl-sum-status data-template=\"Razem dla klienta: {total}\"");
        assertThat(html).contains("data-cl-dirty-status");
    }

    @Test
    void termsCardShowsTheDeadlineWithItsDayCount() {
        // when
        String html = render("<section th:replace=\"~{offers/details/side :: terms(${page})}\"></section>", model(fullOffer()));

        // then
        assertThat(html).containsPattern("<dt>Ważna do</dt>\\s*<dd>24.10.2026, 23:59 \\(5 dni\\)</dd>");
    }

    @Test
    void blankEmailAndPhoneLeaveNoEmptyLinks() {
        // given
        Customer blanks = new Customer("Marta", "", "", "", "", true, null, null, false);
        OfferDetailsPage page = offer(List.of(laptop(0)), summary(false), blanks, false);

        // when
        String html = render("<section th:replace=\"~{offers/details/side :: client(${page})}\"></section>", model(page));

        // then
        assertThat(html).contains("<strong>Marta</strong>").doesNotContain("mailto:").doesNotContain("tel:")
                .doesNotContain("Brak danych kontaktowych klienta");
    }

    @Test
    void lastItemCannotBeRemovedAndOthersPostToRemoveItem() {
        // when
        String single = html(offer(List.of(laptop(0)), summary(false), customer(true, true), true));
        String many = html(fullOffer());

        // then
        assertThat(row(single, 0)).contains("Oferta musi mieć co najmniej jedną pozycję").contains("aria-disabled=\"true\"")
                .doesNotContain("/remove-item/");
        assertThat(row(many, 1)).contains("formaction=\"/dashboard/offer/" + ID + "/remove-item/1?itemId=id-1\"")
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
                .contains("Podaj kod producenta.").contains("Zapisz pozycję").contains("Koszt u dostawców: 611,00 PLN → 642,00 PLN (najniższy → mediana)")
                .contains("href=\"/dashboard/offer/" + ID + "\"")
                .containsPattern("<input class=\"cl-input is-money\"[^>]*id=\"offer-item-cost\"")
                .doesNotContainPattern("<input[^>]*id=\"offer-item-cost\"[^>]*required");
        // the page and the dialog count a cleared cost as 0, as the old row did
        assertThat(html(fullOffer())).containsPattern("id=\"item-edit-cost\"")
                .doesNotContainPattern("<input[^>]*id=\"item-edit-cost\"[^>]*required");
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

    @Test
    void emptyClientAndNoteSitAtTheEdgeOfTheirCard() {
        // given — cl-card-desc brings its own side padding, which inside an inset block doubled the card's
        String html = html(offer(List.of(laptop(0)), summary(false), customer(false, false), false));

        // then
        assertThat(html).contains("<p class=\"cl-help\">Brak danych kontaktowych klienta.</p>");
        assertThat(render("<section th:replace=\"~{offers/details/side :: note(${page})}\"></section>", model(withoutNote())))
                .contains("<p class=\"cl-help\">Brak notatki</p>");
    }

    @Test
    void theItemsFormCarriesTheVersionItWasOpenedAt() {
        // when
        String html = html(fullOffer());

        // then
        String form = html.substring(html.indexOf("id=\"offer-items\""), html.indexOf("class=\"cl-layout-aside\""));
        assertThat(form).contains("<input type=\"hidden\" name=\"version\" value=\"7\" data-cl-offer-version data-cl-dirty-ignore>");
        assertThat(html).doesNotContain("data-cl-offer-conflict");
    }

    @Test
    void aConflictAsksToOverwriteOrLoadTheCurrentVersion() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("conflict", true);
        model.put("returnTo", "/dashboard/offers?segment=expired");

        // when
        String html = render(PAGE, model);

        // then
        String form = html.substring(html.indexOf("id=\"offer-items\""), html.indexOf("class=\"cl-layout-aside\""));
        assertThat(form).contains("data-cl-offer-conflict", "Oferta zmieniła się od otwarcia strony",
                "Twoje zmiany w pozycjach nie zostały zapisane — są niżej.",
                "name=\"overwrite\" value=\"true\"", "Nadpisz moimi zmianami", "Wczytaj aktualną wersję",
                "<a class=\"cl-button\" href=\"/dashboard/offer/" + ID + "?returnTo=");
    }

    @Test
    void theRefreshOfADialogCarriesTheNewVersion() {
        // given
        Map<String, Object> model = model(fullOffer());

        // when
        String html = render("<template th:replace=\"~{offers/details :: refresh}\"></template>", model);

        // then
        assertThat(html).contains("<input type=\"hidden\" data-cl-offer-version value=\"7\">");
    }

    @Test
    void theScriptRemovesARowFromTheTableWithoutAskingAndCanUndoIt() {
        // when
        String single = html(offer(List.of(laptop(0)), summary(false), customer(true, true), true));
        String many = html(fullOffer());

        // then
        assertThat(row(many, 1)).contains("data-cl-line-remove").doesNotContain("data-cl-offer-action")
                .doesNotContain("data-cl-confirm-title").contains("type=\"submit\"");
        assertThat(row(many, 1)).contains("class=\"cl-menu-reason\" id=\"offer-line-1-remove-reason\" hidden");
        assertThat(row(single, 0)).contains("data-cl-line-remove").contains("type=\"button\"")
                .contains("aria-describedby=\"offer-line-0-remove-reason\"");
        assertThat(many).contains("data-msg-line-removed-draft=\"Usunięto „{0}” — zmiana nie jest zapisana\"")
                .contains("data-msg-undo=\"Cofnij\"")
                .contains("data-msg-items-added-draft=\"Dodano {0} — zmiany nie są zapisane\"")
                .contains("data-msg-items-title=\"Pozycje: {n}\"");
    }

    @Test
    void removingAnItemNamesItById() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("returnTo", "/dashboard/offers");

        // when
        String html = render(PAGE, model);

        // then
        assertThat(row(html, 1)).contains("formaction=\"/dashboard/offer/" + ID
                + "/remove-item/1?itemId=id-1&amp;returnTo=/dashboard/offers\"");
    }

    @Test
    void theDraftAnswersTheItemsCardAndWhatWasSkipped() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("skipped", 1);
        model.put("requested", 3);

        // when
        String html = render("<th:block th:replace=\"~{offers/details :: itemsDraft}\"></th:block>", model);

        // then
        assertThat(html).contains("id=\"pozycje\"", "data-cl-line").doesNotContain("id=\"offer-items\"");
        assertThat(html).contains("<template data-cl-draft-meta data-skipped=\"1\" data-requested=\"3\" "
                + "data-message=\"Nie dodano 1 z 3 pozycji — produktu nie ma już w cenniku ani u dostawców.\"></template>");
    }

    @Test
    void aDraftWithNothingSkippedHasNoMessage() {
        // given
        Map<String, Object> model = model(fullOffer());
        model.put("skipped", 0);
        model.put("requested", 2);

        // when
        String html = render("<th:block th:replace=\"~{offers/details :: itemsDraft}\"></th:block>", model);

        // then
        assertThat(html).contains("<template data-cl-draft-meta data-skipped=\"0\" data-requested=\"2\"></template>");
    }

    private static OfferDetailsPage withoutNote() {
        OfferDetailsPage p = offer(List.of(laptop(0)), summary(false), customer(false, false), false);
        return new OfferDetailsPage(p.offerId(), p.storeId(), p.shortId(), p.template(), p.title(), p.backHref(), p.backLabelKey(),
                p.validity(), p.meta(), p.lines(), p.categoryGroups(), p.catalogs(), p.summary(), p.terms(), p.customer(), null,
                p.clientUrl(), p.proformaAvailable(), p.copyWithContactAvailable(), p.recordName(), p.fulfilmentType(),
                p.version());
    }
}
