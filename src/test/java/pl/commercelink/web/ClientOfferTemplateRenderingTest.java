package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.web.clientoffer.ClientOfferDataForm;
import pl.commercelink.web.clientoffer.ClientOfferNotice;
import pl.commercelink.web.dtos.ClientDataDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOfferTemplateRenderingTest {

    private static final String OFFER_PATH = "/store/s1/individual/offer/" + ClientOfferRenderer.OFFER_ID;
    private static final Pattern PAY_BUTTON = Pattern.compile("<button[^>]*>\\s*Order and pay\\s*</button>");

    @Test
    void newOfferShowsThreeStepsWithPaymentLocked() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        assertThat(html).contains("<title>Offer · Nova Tech</title>")
                .contains("<main id=\"clContent\" class=\"cl-page cl-public-main is-form\"")
                .contains("<h1 class=\"cl-page-title\">Your offer</h1>")
                .contains("cl-step-badge is-locked")
                .contains("<form id=\"client-offer-data\"")
                .contains("action=\"" + OFFER_PATH + "/submit\"")
                .contains("Payment unlocks once you save your details in step 2.");
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html.split("class=\"cl-step-badge", -1)).hasSize(4);
        assertThat(payButton(html)).contains("disabled").contains("aria-describedby=\"client-offer-pay-lock\"");
    }

    @Test
    void itemsShowAmountsVariantsAndTheTotal() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        assertThat(html).contains("Items: 3")
                .contains("AMD Ryzen 7 7800X3D")
                .contains("Processors · 1 × 1 699,00 PLN")
                .contains("<span class=\"cl-list-amount\">898,00 PLN</span>")
                .contains("action=\"" + OFFER_PATH + "/select-variant\"")
                .contains("<input type=\"hidden\" name=\"groupId\" value=\"Graphics card\"")
                .contains("Choose a variant: Graphics card")
                .contains("<span class=\"cl-choice-aside\">selected</span>")
                .contains("<span class=\"cl-choice-aside\">+ 1 000,00 PLN</span>")
                .contains("<span class=\"cl-choice-aside\">same price</span>")
                .contains("Change variant")
                .contains("Delivery: DPD courier")
                .contains("fas fa-truck")
                .contains("Total to pay")
                .contains("5 525,99 PLN");
    }

    @Test
    void fieldNamesKeepTheClientDataContract() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        for (String field : List.of("name", "surname", "companyName", "taxId", "streetAndNumber", "postalCode", "city",
                "country", "email", "phone")) {
            assertThat(html).contains("name=\"billingDetails." + field + "\"");
        }
        for (String field : List.of("name", "surname", "companyName", "streetAndNumber", "postalCode", "city",
                "country", "email", "phone")) {
            assertThat(html).contains("name=\"shippingDetails." + field + "\"");
        }
        assertThat(html).contains("name=\"customerType\"").contains("name=\"shippingAddress\"")
                .contains("<input type=\"hidden\" name=\"shippingDetails.country\" value=\"PL\"")
                .contains("data-cl-variant-select=\"customer\"")
                .contains("data-cl-variant-select=\"shipping\"")
                .contains("data-cl-variant-when=\"customer=company\"")
                .contains("data-cl-variant-when=\"shipping=other\"")
                .doesNotContain("name=\"edit\"");
    }

    @Test
    void customerAutocompleteTokensAreSet() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        assertThat(html).contains("autocomplete=\"given-name\"")
                .contains("autocomplete=\"postal-code\"")
                .contains("autocomplete=\"organization\"")
                .contains("autocomplete=\"shipping street-address\"")
                .contains("autocomplete=\"shipping tel\"")
                .doesNotContain("autocomplete=\"off\"");
    }

    @Test
    void errorsAreSummarisedAndShownAtTheFields() {
        // given
        ClientDataDto dto = new ClientDataDto();
        dto.setBillingDetails(billingWithCompany());
        dto.getBillingDetails().setTaxId(null);
        ClientOfferDataForm form = ClientOfferDataForm.of(dto, "company", "same", false);

        // when
        String html = ClientOfferRenderer.newOffer()
                .form(form, Map.of("billingDetails.taxId", "client.offer.error.taxId.required"))
                .render();

        // then
        assertThat(html).contains("cl-alert is-bad")
                .contains("Fix the marked fields")
                .contains("href=\"#billingDetails.taxId\"")
                .contains("aria-invalid=\"true\"")
                .contains("id=\"billingDetails.taxId-error\"")
                .contains("value=\"Nova Biuro\"")
                .containsPattern("value=\"company\"[^>]*checked");
        assertThat(payButton(html)).contains("disabled");
    }

    @Test
    void submittedDataIsMaskedThroughTheSharedFragment() {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing()).render();

        // then
        assertThat(html).contains("Jan********")
                .contains("jan*********************")
                .contains("cl-step-badge is-done")
                .contains("Same as the billing details")
                .contains("href=\"?edit=data#client-offer-data\"")
                .doesNotContain("Kowalski")
                .doesNotContain("kowalski@")
                .doesNotContain("null")
                .doesNotContain("<form id=\"client-offer-data\"");
        assertThat(payButton(html)).doesNotContain("disabled");
    }

    @Test
    void sameShippingIsNamedNotRepeated() {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing()).render();

        // then
        assertThat(html.split("Jan\\*{9}", -1)).hasSize(2);
    }

    @Test
    void otherShippingIsListedMasked() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing());
        renderer.basket().getShippingDetails().setName("Anna");
        renderer.basket().getShippingDetails().setCity("Piaseczno");

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("Ann*********")
                .contains("Pia******")
                .doesNotContain("Piaseczno")
                .doesNotContain("Same as the billing details");
    }

    @Test
    void editModeShowsAnEmptyFormAndKeepsPaymentOpen() {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing()).editing().render();

        // then
        assertThat(html).contains("<form id=\"client-offer-data\"")
                .contains("<input type=\"hidden\" name=\"edit\" value=\"data\"")
                .contains("Your saved details stay until you save new ones.")
                .contains("href=\"" + OFFER_PATH + "\"")
                .doesNotContain("Jan********");
        assertThat(payButton(html)).doesNotContain("disabled");
    }

    @Test
    void hiddenPricesKeepTheTotal() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        renderer.basket().setShowPrices(false);

        // when
        String html = renderer.render();

        // then
        assertThat(html).doesNotContain("cl-list-amount")
                .doesNotContain("Gross prices.")
                .contains("Processors · quantity: 1")
                .contains("<span class=\"cl-choice-aside\">+ 1 000,00 PLN</span>")
                .contains("Total to pay")
                .contains("5 525,99 PLN");
    }

    @Test
    void splitPaymentHidesOnlinePayment() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer().submitted(billingWithCompany());
        InvoicingConfiguration invoicing = new InvoicingConfiguration();
        invoicing.setSplitPaymentsEnabled(true);
        renderer.store().setInvoicingConfiguration(invoicing);
        renderer.basket().getBasketItems().get(0).setUnitPrice(16000.0);

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("cl-alert is-info")
                .contains("Pay by split payment transfer")
                .contains("PL61 1090 1014 0000 0712 1981 2874")
                .doesNotContain("/checkout");
    }

    @Test
    void oneMethodNamesItWithoutAChoice() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing());
        renderer.store().getPayments().remove(1);

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("Payment via Paynow.")
                .doesNotContain("name=\"paymentOptionId\"");
    }

    @Test
    void twoMethodsAreAChoiceWithTheDefaultRecommended() {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing()).render();

        // then
        assertThat(html).contains("action=\"" + OFFER_PATH + "/checkout\"")
                .containsPattern("name=\"paymentOptionId\" value=\"Paynow\"[^>]*checked")
                .contains("Recommended")
                .contains("or a traditional bank transfer");
    }

    @Test
    void noPaymentOptionsHidesThePayButton() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing());
        renderer.store().getPayments().clear();

        // when
        String html = renderer.render();

        // then
        assertThat(html).doesNotContain("Order and pay")
                .doesNotContain("/checkout")
                .doesNotContain("or a traditional bank transfer")
                .contains("The store does not offer online payment");
    }

    @Test
    void noPaymentAndNoBankAccountAsksToContactTheStore() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        renderer.store().getPayments().clear();
        renderer.store().setBankAccounts(new java.util.ArrayList<>());

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("Contact the store about payment.")
                .doesNotContain("cl-kv is-bank")
                .doesNotContain("send-proforma-invoice");
    }

    @Test
    void bankTransferShowsCopyButtonsHiddenUntilScriptAndAProforma() {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing()).render();

        // then
        assertThat(html).contains("Nova Tech sp. z o.o.")
                .contains("data-cl-copy=\"PL61109010140000071219812874\"")
                .contains("data-cl-copy=\"" + ClientOfferRenderer.OFFER_ID + "\"")
                .containsPattern("<button type=\"button\" class=\"cl-copy-inline is-icon\" hidden data-cl-copy-reveal")
                .contains("action=\"" + OFFER_PATH + "/send-proforma-invoice\"")
                .contains("Send a proforma invoice to")
                .contains("Every request issues a new proforma invoice.");
    }

    @Test
    void lockedProformaIsDisabled() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        assertThat(html).contains("We will send the proforma invoice once your details are saved.")
                .containsPattern("<button[^>]*disabled[^>]*>\\s*<i class=\"far fa-envelope\"");
    }

    @Test
    void noticeIsShownInThePaymentCard() {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing())
                .notice(ClientOfferNotice.bad("Payment did not start.")).render();

        // then
        assertThat(html).containsPattern("cl-alert is-bad\" role=\"status\"[\\s\\S]*Payment did not start.");
    }

    @Test
    void personalCollectionSkipsTheShippingAddress() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer().personalCollection();

        // when
        String html = renderer.render();

        // then
        assertThat(html).doesNotContain("name=\"shippingAddress\"")
                .doesNotContain("name=\"shippingDetails.")
                .contains("Personal collection — no delivery address is needed.")
                .contains("fas fa-store");
    }

    @Test
    void expiredOfferShowsOnlyTheNotice() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        renderer.basket().setExpiresAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        BillingDetails storeBilling = new BillingDetails();
        storeBilling.setEmail("shop@nova.pl");
        storeBilling.setPhone("600 700 800");
        renderer.store().setBillingDetails(storeBilling);

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("This offer has expired")
                .contains("Offer 6f0c1a2b · Expired 1 October 2026, 12:00")
                .contains("<a href=\"mailto:shop@nova.pl\">shop@nova.pl</a>")
                .contains("or call:")
                .doesNotContain("cl-list")
                .doesNotContain("/checkout")
                .doesNotContain("<form");
    }

    @Test
    void canEditLinksToThePanel() {
        // when
        String html = ClientOfferRenderer.newOffer().canEdit().render();

        // then
        assertThat(html).contains("href=\"/dashboard/offer/" + ClientOfferRenderer.OFFER_ID + "\"")
                .contains("Edit in the dashboard");
    }

    @Test
    void polishPageUsesPolishTexts() {
        // when
        String html = ClientOfferRenderer.newOffer().locale(Locale.forLanguageTag("pl")).render();

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Twoja oferta</h1>")
                .contains("Razem do zapłaty")
                .contains("Polska")
                .doesNotContain("??");
    }

    @Test
    void noInlineScriptsStylesOrCurrencyLiteral() throws IOException {
        // when
        String html = ClientOfferRenderer.newOffer().submitted(ClientOfferRenderer.billing()).render();

        // then
        String content = html.substring(html.indexOf("<main"), html.indexOf("</main>"));
        assertThat(html).doesNotContain("<script>").doesNotContain("??");
        assertThat(content).doesNotContain("<style").doesNotContain(" style=")
                .doesNotContainPattern(" on[a-z]+=");
        for (Path source : templateSources()) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8)).as(source.toString())
                    .doesNotContain("PLN")
                    .doesNotContain("<script>")
                    .doesNotContain("style=")
                    .doesNotContain("utext");
        }
    }

    @Test
    void scriptsAreLoaded() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        assertThat(html).contains("/js/client-offer.js")
                .contains("/js/variant-fields.js")
                .contains("/js/submit-once.js")
                .contains("/js/error-summary.js")
                .contains("/js/copy-field.js");
    }

    static List<Path> templateSources() throws IOException {
        try (Stream<Path> fragments = Files.list(Path.of("src/main/resources/templates/client-offer"))) {
            return Stream.concat(Stream.of(Path.of("src/main/resources/templates/clientOffer.html")),
                    fragments.filter(p -> !p.getFileName().toString().equals("unavailable.html"))).toList();
        }
    }

    private static String payButton(String html) {
        Matcher matcher = PAY_BUTTON.matcher(html);
        assertThat(matcher.find()).as("pay button").isTrue();
        return matcher.group();
    }

    private static BillingDetails billingWithCompany() {
        BillingDetails billing = ClientOfferRenderer.billing();
        billing.setCompanyName("Nova Biuro");
        billing.setTaxId("5252814410");
        return billing;
    }
}
