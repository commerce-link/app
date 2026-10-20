package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.springframework.util.unit.DataSize;
import org.thymeleaf.context.Context;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OfferCreateTemplateRenderingTest {

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private String renderContact(String intent, String sourceId, ContactDetails contact, String offerName, String errorMessage) {
        return renderContact(intent, sourceId, sourceOf(intent), contact, offerName, errorMessage);
    }

    /** What the controller decides from the source it loaded; the tests pass it the way the source's type would. */
    private static String sourceOf(String intent) {
        return "template".equals(intent) ? "template" : null;
    }

    private String renderContact(String intent, String sourceId, String source, ContactDetails contact, String offerName,
                                 String errorMessage) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("intent", intent);
        context.setVariable("sourceId", sourceId);
        context.setVariable("source", source);
        context.setVariable("contactDetails", contact);
        context.setVariable("offerName", offerName);
        context.setVariable("errorMessage", errorMessage);
        return engine.process("offerContact", context);
    }

    private String renderCsv(OfferCreationDto form, String errorMessage) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("form", form);
        context.setVariable("uploadLimit", DataSize.ofMegabytes(10));
        context.setVariable("errorMessage", errorMessage);
        return engine.process("newOffer_from_csv", context);
    }

    private static ContactDetails contact(String name, String surname, String company, String email, String phone) {
        ContactDetails contact = new ContactDetails();
        contact.setName(name);
        contact.setSurname(surname);
        contact.setCompanyName(company);
        contact.setEmail(email);
        contact.setPhone(phone);
        return contact;
    }

    @Test
    void emptyOfferIsADesignSystemSubpageThatPostsTheSameFields() {
        // when
        String html = renderContact("manual", null, new ContactDetails(), null, null);

        // then
        assertThat(html).contains("cl-page-body is-form").contains("class=\"cl-back\" href=\"/dashboard/offers\"");
        assertThat(html.split("<h1").length - 1).isEqualTo(1);
        assertThat(html).contains(">New offer<").contains("You will add products after creating it.");
        assertThat(html).contains("action=\"/dashboard/offer/new\"").contains("method=\"post\"").contains("data-cl-form-check");
        assertThat(html).contains("name=\"intent\" value=\"manual\"").contains("name=\"sourceId\"");
        assertThat(html).contains("name=\"offerName\"").contains("name=\"name\"").contains("name=\"surname\"")
                .contains("name=\"companyName\"").contains("name=\"email\"").contains("name=\"phone\"").contains("name=\"gclid\"");
        assertThat(html).contains("id=\"offerName\" name=\"offerName\" type=\"text\" autocomplete=\"off\" required");
        assertThat(html).contains("data-cl-any-of=\"contact-name contact-company\"").contains("data-cl-any-of=\"contact-email contact-phone\"");
        assertThat(html).contains("autofocus").contains(">Create offer<").contains("href=\"/dashboard/offers\">Cancel<");
        assertThat(html).doesNotContain("class=\"box\"").doesNotContain("is-primary\" type=\"submit\" th:")
                .doesNotContain("class=\"button").doesNotContain("cl-alert is-bad\" tabindex=\"-1\" data-cl-error-summary");
        assertThat(html).doesNotContain("<details class=\"cl-disclosure cl-card-extras\" id=\"offer-new-extras\" open");
    }

    @Test
    void contactFieldsLeaveAutofillOffBecauseTheyHoldTheCustomersDetails() {
        // when
        String html = renderContact("manual", null, new ContactDetails(), null, null);

        // then
        assertThat(html.split("autocomplete=\"off\"").length - 1).isEqualTo(7);
        assertThat(html).contains("type=\"email\" inputmode=\"email\"").contains("type=\"tel\" inputmode=\"tel\"");
    }

    @Test
    void basketCopyShowsTheCopiedCustomerAndLeadsBackToTheBasket() {
        // given
        ContactDetails prefilled = contact("Anna", "Kowalska", null, "anna@example.com", "+48 601 234 567");

        // when
        String html = renderContact("copy", "b-123", "basket", prefilled, "Basket 7f3a91c2", null);

        // then
        assertThat(html).contains("class=\"cl-back\" href=\"/dashboard/basket/view/b-123\"").contains(">Basket<");
        assertThat(html).contains("check them before creating the offer");
        assertThat(html).contains("value=\"Anna\"").contains("value=\"Kowalska\"").contains("value=\"anna@example.com\"")
                .contains("value=\"+48 601 234 567\"").contains("value=\"Basket 7f3a91c2\"");
        assertThat(html).contains("name=\"intent\" value=\"copy\"").contains("name=\"sourceId\" value=\"b-123\"");
        assertThat(html).contains("href=\"/dashboard/basket/view/b-123\">Cancel<");
        assertThat(html).doesNotContain("autofocus");
    }

    @Test
    void templateAndOfferCopiesLeadBackToTheirSource() {
        // when
        String template = renderContact("template", "t-1", new ContactDetails(), null, null);
        String offerCopy = renderContact("copy", "o-1", "offer", new ContactDetails(), null, null);
        String templateCopy = renderContact("copy", "t-1", "template", new ContactDetails(), null, null);

        // then
        assertThat(template).contains("class=\"cl-back\" href=\"/dashboard/offer/t-1\"").contains(">Template<")
                .contains("copied from the template");
        assertThat(offerCopy).contains("class=\"cl-back\" href=\"/dashboard/offer/o-1\"").contains(">Offer<")
                .contains("copied from the offer");
        assertThat(templateCopy).contains("class=\"cl-back\" href=\"/dashboard/offer/t-1\"").contains(">Template<");
    }

    @Test
    void sourceIdIsEncodedInTheWayBack() {
        // when
        String html = renderContact("template", "a/b c", new ContactDetails(), null, null);

        // then
        assertThat(html).contains("href=\"/dashboard/offer/a%2Fb%20c\"");
    }

    @Test
    void refusalFromTheServerIsShownAboveTheCard() {
        // when
        String html = renderContact("manual", null, new ContactDetails(), null, "Missing contact details.");

        // then
        assertThat(html).contains("id=\"offer-new-server-error\" class=\"cl-alert is-bad\"").contains("data-cl-error-summary")
                .contains("Missing contact details.");
        assertThat(html).doesNotContain("autofocus");
    }

    @Test
    void csvOfferPostsTheFileTypeAndContactAsBefore() {
        // given
        OfferCreationDto form = new OfferCreationDto();
        form.setType("CSV");

        // when
        String html = renderCsv(form, null);

        // then
        assertThat(html).contains(">New offer from a CSV file<").contains("class=\"cl-back\" href=\"/dashboard/offers\"");
        assertThat(html).contains("action=\"/dashboard/offer/new/csv\"").contains("enctype=\"multipart/form-data\"");
        assertThat(html).contains("<input type=\"hidden\" name=\"type\" value=\"CSV\">");
        assertThat(html).contains("type=\"file\" id=\"csvFile\" name=\"csvFile\" accept=\".csv,text/csv\" required");
        assertThat(html).contains("name=\"offerName\"").contains("name=\"contactDetails.name\"").contains("name=\"contactDetails.surname\"")
                .contains("name=\"contactDetails.companyName\"").contains("name=\"contactDetails.email\"")
                .contains("name=\"contactDetails.phone\"").contains("name=\"gclid\"");
        assertThat(html).doesNotContain("readonly").doesNotContain("Order reference type");
    }

    @Test
    void csvPageExplainsTheFormatTheImporterReads() {
        // when
        String html = renderCsv(new OfferCreationDto(), null);

        // then
        assertThat(html).contains("id=\"offer-csv-format\"").contains("How to prepare the file?");
        assertThat(html).contains("1. Category").contains("4. Gross price (PLN)").contains("in whole zloty, no grosze").contains("7. Service");
        assertThat(html).contains("class=\"cl-code-block\"").contains("category;name;quantity;price;cost;code;service\n");
        assertThat(html).contains("href=\"/files/offer-import-example.csv\" download");
    }

    @Test
    void csvGclidOpensTheTrackingSectionWhenItHasAValue() {
        // given
        OfferCreationDto form = new OfferCreationDto();
        form.setGclid("abc");

        // when
        String html = renderCsv(form, "Invalid CSV file format");

        // then
        assertThat(html).contains("id=\"offer-new-extras\" open").contains("value=\"abc\"");
        assertThat(html).contains("Invalid CSV file format");
    }

    @Test
    void bothPagesTakeTheRefusalOffTheFrameBanner() throws Exception {
        // when
        String contact = Files.readString(Path.of("src/main/resources/templates/offerContact.html"));
        String csv = Files.readString(Path.of("src/main/resources/templates/newOffer_from_csv.html"));

        // then
        assertThat(contact).contains("layout:decorate=\"~{layout(ownsErrorMessage=true)}\"");
        assertThat(csv).contains("layout:decorate=\"~{layout(ownsErrorMessage=true)}\"");
    }

    @Test
    void basketBackLinkComesFromTheSourceNotFromTheOfferName() {
        // when
        String offerCopyWithAName = renderContact("copy", "o-1", "offer", new ContactDetails(), "Named copy", null);

        // then
        assertThat(offerCopyWithAName).contains("class=\"cl-back\" href=\"/dashboard/offer/o-1\"")
                .doesNotContain("/dashboard/basket/view/");
    }

    @Test
    void csvFileFieldChecksTheUploadLimitAndReadsOutTheChosenName() {
        // when
        String html = renderCsv(new OfferCreationDto(), null);

        // then
        assertThat(html).contains("aria-describedby=\"csvFile-name csvFile-help\"").contains("id=\"csvFile-name\"");
        assertThat(html).contains("data-cl-max-bytes=\"10485760\"")
                .contains("data-cl-size-message=\"The file is too large: the limit is 10 MB.");
        assertThat(html).contains("UTF-8").contains("data-cl-form-check-summary");
    }

    @Test
    void exampleFileMatchesTheImporterFormat() throws Exception {
        // when
        String example = Files.readString(Path.of("src/main/resources/static/files/offer-import-example.csv"));

        // then
        assertThat(example).startsWith("﻿kategoria;nazwa;ilosc;cena;koszt;kod;usluga");
        assertThat(example.lines().skip(1)).allSatisfy(row -> assertThat(row.split(";", -1)).hasSize(7));
    }
}
