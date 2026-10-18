package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.DefaultTemplateResolver;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.MarketplaceDecision;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAItemsForm;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.orders.rma.RmaShipmentsView;
import pl.commercelink.web.rma.RmaDetailPage;
import pl.commercelink.web.rma.RmaDetailPageFactory;
import pl.commercelink.web.rma.RmaNotice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page frame of rma-detail.html: record header, notices and layout. The blocks not redesigned yet live in
 * rma/detail/legacy.html, whose th:field bindings need a Spring request context; here it is replaced by a stub with the
 * same fragment names, so only the new markup is rendered and checked.
 */
class RmaDetailPageRenderingTest {

    private static final String RMA_ID = "rma-1abc-2222-3333-4444-555566667777";
    private static final String CLIENT_LINK = "https://app.example/store/store-1/client/rma/" + RMA_ID;
    private static final String LEGACY_STUB = """
            <html><body>
            <div th:fragment="details">LEGACY-DETAILS</div>
            <div th:fragment="events">LEGACY-EVENTS</div>
            <div th:fragment="marketplace">LEGACY-MARKETPLACE</div>
            <th:block th:fragment="scripts">LEGACY-SCRIPTS</th:block>
            </body></html>""";

    private RmaDetailPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        factory = new RmaDetailPageFactory(messages);
    }

    private static RMA rma(RMAStatus status) {
        RMA rma = new RMA("store-1");
        rma.setRmaId(RMA_ID);
        rma.setOrderId("order-1-aaaa-bbbb");
        rma.setStatus(status);
        rma.setEmail("jan@example.com");
        rma.setCreatedAt(LocalDateTime.of(2026, 10, 12, 14, 3));
        ShippingDetails address = new ShippingDetails();
        address.setName("Jan");
        address.setSurname("Kowalski");
        rma.setShippingDetails(address);
        return rma;
    }

    private RmaDetailPage page(RMA rma) {
        return factory.page("store-1", rma, List.of(), List.of(), null, false, CLIENT_LINK, null, null, Locale.ENGLISH);
    }

    private RmaDetailPage page(RMA rma, List<RMAItem> items, List<OrderItem> remaining, boolean refundDeliveryDefault) {
        return factory.page("store-1", rma, items, remaining, null, refundDeliveryDefault, CLIENT_LINK, null, null,
                Locale.ENGLISH);
    }

    private static RMAItem item(String id, String name, RMAItemStatus status, int qty) {
        RMAItem item = new RMAItem();
        item.setRmaId(RMA_ID);
        item.setRmaItemId(id);
        item.setItemId("order-item-" + id);
        item.setName(name);
        item.setStatus(status);
        item.setQty(qty);
        item.setPrice(100);
        item.setTax(0.23);
        item.setEan("5901234123457");
        item.setReason("Does not work");
        item.setDesiredResolution(RMAResolutionType.Return);
        return item;
    }

    private static List<RMAItem> twoItems() {
        return List.of(item("i-1", "Headset", RMAItemStatus.Received, 1), item("i-2", "Mouse", RMAItemStatus.New, 2));
    }

    private static OrderItem orderItem() {
        OrderItem orderItem = new OrderItem();
        orderItem.setItemId("left-1");
        orderItem.setName("Cable");
        orderItem.setQty(1);
        return orderItem;
    }

    private static String render(String template, RmaDetailPage page, RmaNotice notice) {
        return render(template, page, notice, new RMAItemsForm(new ArrayList<>()));
    }

    private static String render(String template, RmaDetailPage page, RmaNotice notice, RMAItemsForm form) {
        return render(template, page, notice, form, shipments(page.closed()));
    }

    private static String render(String template, RmaDetailPage page, RmaNotice notice, RMAItemsForm form,
                                 RmaShipmentsView shipments) {
        TemplateEngine engine = EnglishFragmentTemplateEngine.create();
        DefaultTemplateResolver legacy = new DefaultTemplateResolver();
        legacy.setOrder(0);
        legacy.setTemplateMode(TemplateMode.HTML);
        legacy.setResolvablePatterns(Set.of("rma/detail/legacy"));
        legacy.setTemplate(LEGACY_STUB);
        engine.addTemplateResolver(legacy);
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("page", page);
        context.setVariable("rmaNotice", notice);
        context.setVariable("rmaItemsForm", form);
        context.setVariable("rmaShipments", shipments);
        return engine.process("<div th:replace=\"~{" + template + "}\"></div>", context);
    }

    @Test
    void headerShowsShortIdStatusNextActionAndMoreMenu() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.ItemsReceived));

        // when
        String html = render("rma-detail :: body", page, null);

        // then
        assertThat(html).containsPattern("<h1 class=\"cl-page-title\">RMA rma-1abc</h1>");
        assertThat(html).contains("data-cl-copy=\"" + RMA_ID + "\"");
        assertThat(html).contains("<span class=\"cl-status is-warn\">Items received</span>");
        assertThat(html).containsPattern("class=\"cl-button is-primary\" href=\"\\?open=next\" data-cl-dialog-open=\"next-dialog\"");
        assertThat(html).contains("Start processing");
        assertThat(html).contains("id=\"rma-more-menu\"");
        assertThat(html).contains("href=\"" + CLIENT_LINK + "\"").contains("target=\"_blank\"");
        assertThat(html).contains("href=\"?open=status\"").contains("data-cl-dialog-open=\"status-dialog\"");
        assertThat(html).contains("href=\"?open=reject\"").contains("data-cl-dialog-open=\"reject-dialog\"");
        assertThat(html).contains("<strong>Jan Kowalski</strong>").contains("href=\"/dashboard/orders/order-1-aaaa-bbbb\"");
        assertThat(html).contains("Reported 12.10.2026 14:03");
        assertThat(html).contains("href=\"/dashboard/rma\"");
    }

    @Test
    void aClosedRmaHasNoPrimaryActionAndAGreyedStatusChange() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.Completed));

        // when
        String html = render("rma-detail :: body", page, null);

        // then
        assertThat(html).doesNotContain("cl-button is-primary").doesNotContain("next-dialog");
        assertThat(html).contains("aria-disabled=\"true\"").contains("cl-menu-reason");
        assertThat(html).doesNotContain("reject-dialog").doesNotContain("status-dialog");
        assertThat(html).contains("href=\"" + CLIENT_LINK + "\"");
    }

    @Test
    void aMarketplaceRmaWithDecisionsOffersTheResendWithAConfirmation() {
        // given
        RMA rma = rma(RMAStatus.Completed);
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("ret-1");
        rma.setExternalReturnReference("RT-5512-0931");
        MarketplaceDecision decision = new MarketplaceDecision();
        decision.setType("ReturnAccepted");
        decision.setCommandId("cmd-1");
        rma.setMarketplaceDecisions(new ArrayList<>(List.of(decision)));

        // when
        String html = render("rma-detail :: body", page(rma), null);

        // then
        assertThat(html).contains("href=\"/dashboard/rma/" + RMA_ID + "/resend-marketplace-decision\"")
                .contains("data-cl-confirm-tone=\"primary\"").contains("id=\"cl-confirm-dialog\"");
        assertThat(html).contains("Allegro: return RT-5512-0931");
    }

    @Test
    void aPageNoticeRendersAboveTheLayoutAndAnItemsNoticeDoesNot() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.Processing));
        RmaNotice pageNotice = new RmaNotice(RmaNotice.OK, "PAGE-NOTICE", List.of(), RmaNotice.Scope.PAGE);
        RmaNotice itemsNotice = new RmaNotice(RmaNotice.WARN, "ITEMS-NOTICE", List.of("Skipped 1: X"), RmaNotice.Scope.ITEMS);

        // when
        String withPageNotice = render("rma-detail :: body", page, pageNotice);
        String withItemsNotice = render("rma-detail :: body", page, itemsNotice);

        // then
        assertThat(withPageNotice.indexOf("PAGE-NOTICE")).isPositive()
                .isLessThan(withPageNotice.indexOf("cl-layout-aside"));
        assertThat(withPageNotice).contains("cl-alert is-ok");
        assertThat(withItemsNotice.indexOf("ITEMS-NOTICE")).isGreaterThan(withItemsNotice.indexOf("cl-layout-aside"));
        assertThat(withItemsNotice).contains("<li>Skipped 1: X</li>");
    }

    @Test
    void aBareRmaRendersWithoutMissingMessages() {
        // given
        RMA bare = new RMA("store-1");
        bare.setRmaId(RMA_ID);

        // when
        String html = render("rma-detail :: body", page(bare), null);

        // then
        assertThat(html).doesNotContain("??").doesNotContain("style=").doesNotContain("onclick");
        assertThat(html).contains("LEGACY-DETAILS").contains("LEGACY-EVENTS").contains("id=\"przesylki\"")
                .contains("id=\"address-title\"");
        assertThat(html).contains("class=\"cl-list-empty\"").doesNotContain("LEGACY-ITEMS");
    }

    @Test
    void itemsTableHasTheSelectionContractAndHiddenFormFields() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = page(rma(RMAStatus.Processing), items, List.of(), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));

        // then
        assertThat(html).contains("id=\"rmaItemsForm\"").contains("data-cl-select-table=\"true\"")
                .contains("data-cl-selection-bar").contains("data-cl-select-clear").contains("data-cl-enter-guard");
        assertThat(html).contains("name=\"rmaItems[0].selected\"").contains("name=\"rmaItems[0].rmaItemId\" value=\"i-1\"")
                .contains("name=\"rmaItems[0].rmaId\" value=\"" + RMA_ID + "\"")
                .contains("name=\"rmaItems[1].itemId\" value=\"order-item-i-2\"").contains("name=\"rmaItems[1].tax\" value=\"0.23\"");
        assertThat(html).contains("data-status=\"Received\"").contains("data-status=\"New\"").contains("data-qty=\"2\"")
                .contains("data-total=\"200.0\"").contains("data-id=\"i-2\"").contains("data-name=\"Mouse\"");
        assertThat(html).contains("data-cl-rma-action=\"accept\"").contains("data-accepts=\"Received\"")
                .contains("data-single-min-qty=\"2\"").contains("data-skip-New=\"not received yet\"");
        assertThat(html).contains("Items (2)").contains("Client wants: return · unresolved").contains("2 × 100,00 PLN");
        assertThat(html).doesNotContain("??").doesNotContain("LEGACY-ITEMS");
    }

    @Test
    void everyBulkActionPostsToItsUnchangedAddress() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = page(rma(RMAStatus.Processing), items, List.of(), false);
        String base = "formaction=\"/dashboard/rma/" + RMA_ID + "/";

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));

        // then
        assertThat(html).contains(base + "markItemsAsReceived\"").contains(base + "acceptReturn\"")
                .contains(base + "replaceItems?broken=false\"").contains(base + "replaceItems?broken=true\"")
                .contains(base + "shipping/shipItemsToDistributor\"").contains(base + "shipping/shipItemsToClient\"")
                .contains(base + "markItemsAsSentToDistributor\"").contains(base + "markItemsAsReturnedToClient\"")
                .contains(base + "checkSerialNumbers\"").contains(base + "deleteItems\"");
        for (String name : new String[]{"received", "accept", "replace-working", "replace-broken", "ship-distributor",
                "ship-client", "mark-distributor", "mark-client", "split", "delete"}) {
            assertThat(html).as(name).contains("id=\"" + name + "-dialog\"").contains("data-cl-dialog-open=\"" + name + "-dialog\"");
        }
    }

    @Test
    void refundDeliveryIsOfferedOnlyOnAMarketplaceRma() {
        // given
        List<RMAItem> items = twoItems();
        RMA marketplace = rma(RMAStatus.Processing);
        marketplace.setMarketplace("Allegro");
        marketplace.setExternalReturnId("ret-1");

        // when
        String shop = render("rma-detail :: body", page(rma(RMAStatus.Processing), items, List.of(), false), null,
                new RMAItemsForm(items));
        String refunded = render("rma-detail :: body", page(marketplace, items, List.of(), true), null, new RMAItemsForm(items));
        String notRefunded = render("rma-detail :: body", page(marketplace, items, List.of(), false), null, new RMAItemsForm(items));

        // then
        assertThat(shop).doesNotContain("name=\"refundDelivery\"");
        assertThat(inputs(refunded, "refundDelivery")).hasSize(2).allMatch(input -> input.contains("checked"));
        assertThat(inputs(notRefunded, "refundDelivery")).hasSize(2).noneMatch(input -> input.contains("checked"));
        assertThat(refunded).contains("Allegro will refund the buyer");
    }

    @Test
    void aClosedRmaHasNoCheckboxesNoBarAndNoAdd() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = page(rma(RMAStatus.Completed), items, List.of(orderItem()), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));

        // then
        assertThat(html).contains("Headset").contains("Mouse").contains("cl-table is-compact is-wrap is-rma-detail");
        assertThat(html).doesNotContain("data-cl-select-row").doesNotContain("data-cl-selection-bar")
                .doesNotContain("rmaItemsForm").doesNotContain("?open=add").doesNotContain("data-cl-select-table")
                .doesNotContain("<noscript");
    }

    @Test
    void anOpenRmaWithOrderItemsLeftOffersAdd() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.Processing), twoItems(), List.of(orderItem()), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(twoItems()));

        // then
        assertThat(html).contains("href=\"?open=add\"").contains("data-cl-dialog-open=\"add-dialog\"")
                .doesNotContain("data-cl-rma-legacy-add");
        assertThat(dialog(html, "add-dialog")).contains("action=\"/dashboard/rma/" + RMA_ID + "/add-item\"");
    }

    @Test
    void itemNamesWithMarkupAreEscapedInRowsAndDataAttributes() {
        // given
        List<RMAItem> items = List.of(item("i-1", "<b>\"x\"</b>", RMAItemStatus.Received, 1));
        RmaDetailPage page = page(rma(RMAStatus.Processing), items, List.of(), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));

        // then
        assertThat(html).doesNotContain("<b>\"x\"</b>");
        assertThat(html).contains(">&lt;b&gt;&quot;x&quot;&lt;/b&gt;<").contains("data-name=\"&lt;b&gt;&quot;x&quot;&lt;/b&gt;\"");
    }

    @Test
    void itemsNoticeRendersInsideTheItemsCard() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = page(rma(RMAStatus.Processing), items, List.of(), false);
        RmaNotice notice = new RmaNotice(RmaNotice.WARN, "ITEMS-NOTICE", List.of("Skipped 1: X"), RmaNotice.Scope.ITEMS);

        // when
        String html = render("rma-detail :: body", page, notice, new RMAItemsForm(items));

        // then
        assertThat(html.indexOf("ITEMS-NOTICE")).isGreaterThan(html.indexOf("id=\"items-title\""))
                .isLessThan(html.indexOf("<table"));
        assertThat(html.indexOf("ITEMS-NOTICE")).isEqualTo(html.lastIndexOf("ITEMS-NOTICE"));
    }

    @Test
    void noscriptOffersEveryActionAndSplitLinks() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = page(rma(RMAStatus.Processing), items, List.of(), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));

        // then
        Matcher bar = Pattern.compile("(?s)<noscript>\\s*<div class=\"cl-selection-actions is-static\">.*?</noscript>").matcher(html);
        assertThat(bar.find()).isTrue();
        String noscript = bar.group();
        for (String action : new String[]{"markItemsAsReceived", "acceptReturn", "replaceItems?broken=false",
                "replaceItems?broken=true", "shipping/shipItemsToDistributor", "shipping/shipItemsToClient",
                "markItemsAsSentToDistributor", "markItemsAsReturnedToClient", "checkSerialNumbers", "deleteItems"}) {
            assertThat(noscript).as(action).contains("formaction=\"/dashboard/rma/" + RMA_ID + "/" + action + "\"");
        }
        assertThat(noscript).contains("<select").contains("name=\"condition\"");
        assertThat(html).contains("href=\"?open=split&amp;item=i-2\"").doesNotContain("item=i-1\"");
    }

    @Test
    void theDeleteDialogStartsOnCancelAndOtherDialogsDoNot() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = page(rma(RMAStatus.Processing), items, List.of(), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));

        // then
        String delete = dialog(html, "delete-dialog");
        assertThat(delete).containsPattern("<button type=\"button\" class=\"cl-button\" data-cl-dialog-close autofocus=\"autofocus\">");
        assertThat(delete).doesNotContainPattern("<button type=\"submit\"[^>]*autofocus");
        assertThat(dialog(html, "accept-dialog")).doesNotContain("autofocus");
    }

    @Test
    void everyFormPostingToTheDetailsActionCarriesTheFullFieldSet() {
        // given
        RMA rma = rma(RMAStatus.ItemsReceived);
        rma.setMedia(List.of("store-1/rma/x/photo.jpg", "store-1/rma/x/invoice.pdf"));
        rma.setRejectionReason("Old reason");
        rma.setShippingInsurance(4247);
        RmaDetailPage page = page(rma, twoItems(), List.of(orderItem()), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(twoItems()));

        // then
        Matcher forms = Pattern.compile("(?s)<form[^>]*action=\"/dashboard/rma/" + RMA_ID + "\"[^>]*>.*?</form>").matcher(html);
        List<String> found = new ArrayList<>();
        while (forms.find()) {
            found.add(forms.group());
        }
        assertThat(found).hasSize(3);
        for (String form : found) {
            assertThat(form).contains("data-cl-rma-details-form");
            for (String field : new String[]{"status", "email", "rejectionReason", "emailNotificationsEnabled", "shippingInsurance"}) {
                assertThat(form).as(field).contains("name=\"" + field + "\"");
            }
            assertThat(inputs(form, "existingMedia")).hasSize(2);
            assertThat(form).contains("value=\"store-1/rma/x/photo.jpg\"").contains("value=\"store-1/rma/x/invoice.pdf\"");
            assertThat(inputs(form, "emailNotificationsEnabled")).singleElement().asString().contains("value=\"false\"");
            assertThat(inputs(form, "shippingInsurance")).singleElement().asString().contains("value=\"4247.00\"");
            assertThat(inputs(form, "email")).singleElement().asString().contains("value=\"jan@example.com\"");
        }
        assertThat(dialog(html, "next-dialog")).contains("name=\"status\" value=\"Processing\"")
                .contains("name=\"rejectionReason\" value=\"Old reason\"");
        assertThat(dialog(html, "reject-dialog")).contains("name=\"status\" value=\"Rejected\"")
                .doesNotContain("name=\"rejectionReason\" value=");
    }

    @Test
    void theStatusDialogLeavesOutRejectedAndDisablesItemsReceivedWhileProcessing() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.Processing));

        // when
        String html = render("rma-detail :: body", page, null);

        // then
        String status = dialog(html, "status-dialog");
        List<String> radios = inputs(status, "status");
        assertThat(radios).hasSize(6).noneMatch(radio -> radio.contains("value=\"Rejected\""));
        assertThat(radios).filteredOn(radio -> radio.contains("value=\"ItemsReceived\"")).singleElement().asString()
                .contains("disabled");
        assertThat(radios).filteredOn(radio -> radio.contains("value=\"Processing\"")).singleElement().asString()
                .contains("checked");
        assertThat(radios).filteredOn(radio -> radio.contains("disabled")).hasSize(1);
        assertThat(radios).filteredOn(radio -> radio.contains("checked")).hasSize(1);
        assertThat(status).contains("type=\"radio\"").doesNotContain("type=\"hidden\" name=\"status\"");
        assertThat(status).contains("Not available once processing has started.");
        assertThat(status).doesNotContain("open=\"open\"");
    }

    @Test
    void rejectDialogLimitsTheReasonOnlyOnAMarketplaceRma() {
        // given
        RMA marketplace = rma(RMAStatus.New);
        marketplace.setExternalReturnId("r-1");
        marketplace.setMarketplace("Allegro");

        // when
        String store = dialog(render("rma-detail :: body", page(rma(RMAStatus.New)), null), "reject-dialog");
        String market = dialog(render("rma-detail :: body", page(marketplace), null), "reject-dialog");

        // then
        assertThat(textarea(store, "rejectionReason")).doesNotContain("maxlength").doesNotContain("data-cl-rma-counter")
                .contains("required");
        assertThat(textarea(market, "rejectionReason")).contains("maxlength=\"250\"").contains("data-cl-rma-counter")
                .contains("required");
        assertThat(market).contains("Allegro");
        assertThat(store).contains("class=\"cl-button is-danger\"");
    }

    @Test
    void openStatusRendersTheDialogOpenWithoutJavaScript() {
        // given
        RmaDetailPage page = factory.page("store-1", rma(RMAStatus.New), List.of(), List.of(), null, false, CLIENT_LINK,
                "status", null, Locale.ENGLISH);

        // when
        String html = render("rma-detail :: body", page, null);

        // then
        assertThat(dialog(html, "status-dialog")).containsPattern("<dialog[^>]*open=\"open\"");
        assertThat(dialog(html, "next-dialog")).doesNotContainPattern("<dialog[^>]*open=");
        assertThat(dialog(html, "reject-dialog")).doesNotContainPattern("<dialog[^>]*open=");
    }

    @Test
    void openSplitRendersTheSplitFormWithItsAction() {
        // given
        List<RMAItem> items = twoItems();
        RmaDetailPage page = factory.page("store-1", rma(RMAStatus.Processing), items, List.of(), null, false, CLIENT_LINK,
                "split", "i-2", Locale.ENGLISH);
        RmaDetailPage withoutSplit = page(rma(RMAStatus.Processing), items, List.of(), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(items));
        String plain = render("rma-detail :: body", withoutSplit, null, new RMAItemsForm(items));

        // then
        String split = dialog(html, "split-form-dialog");
        assertThat(split).containsPattern("<dialog[^>]*open=\"open\"")
                .contains("action=\"/dashboard/rma/" + RMA_ID + "/items/i-2/split\"")
                .contains("name=\"qty1\"").contains("name=\"qty2\"").contains("Mouse");
        assertThat(html.indexOf("id=\"split-form-dialog\"")).isGreaterThan(html.indexOf("</form>", html.indexOf("id=\"rmaItemsForm\"")));
        assertThat(plain).doesNotContain("split-form-dialog");
    }

    @Test
    void addDialogKeepsTheNewRmaResolutionOrder() {
        // given
        OrderItem cable = orderItem();
        cable.setPrice(19.99);
        RmaDetailPage page = page(rma(RMAStatus.Processing), twoItems(), List.of(cable), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(twoItems()));

        // then
        String add = dialog(html, "add-dialog");
        List<String> radios = inputs(add, "desiredResolution");
        assertThat(radios).hasSize(3);
        assertThat(radios.get(0)).contains("value=\"Repair\"").contains("checked");
        assertThat(radios.get(1)).contains("value=\"Replacement\"");
        assertThat(radios.get(2)).contains("value=\"Return\"");
        assertThat(add).containsPattern("<select[^>]*name=\"orderItemId\"[^>]*data-cl-rma-add");
        assertThat(add).containsPattern("<option value=\"left-1\"[^>]*data-max-qty=\"1\"[^>]*data-unit-price=\"19.99\"");
        assertThat(add).contains("name=\"quantity\"").contains("name=\"reason\"");
        assertThat(add).doesNotContain("data-cl-rma-details-form");
    }

    @Test
    void finishingAProcessingRmaWarnsAboutUnresolvedItems() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.Processing), twoItems(), List.of(), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(twoItems()));

        // then
        String next = dialog(html, "next-dialog");
        assertThat(next).contains("name=\"status\" value=\"Completed\"").contains("cl-alert is-warn").contains(": 2.");
    }

    @Test
    void aClosedRmaRendersNoSingleDialogs() {
        // given
        RmaDetailPage page = page(rma(RMAStatus.Rejected), twoItems(), List.of(orderItem()), false);

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(twoItems()));

        // then
        assertThat(html).doesNotContain("id=\"next-dialog\"").doesNotContain("id=\"status-dialog\"")
                .doesNotContain("id=\"reject-dialog\"").doesNotContain("id=\"add-dialog\"")
                .doesNotContain("data-cl-rma-details-form");
    }

    private static RmaShipmentsView shipments(boolean closed, RmaShipmentsView.Row... rows) {
        return new RmaShipmentsView(List.of(rows), null, null, !closed, "/dashboard/rma/" + RMA_ID + "/updateShipments",
                List.of(ShipmentType.values()));
    }

    private static Shipment typedShipment(String externalId, String trackingNo, LocalDateTime shippedAt) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setExternalId(externalId);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo(trackingNo);
        shipment.setTrackingUrl("https://tracking.example/" + trackingNo);
        shipment.setShippedAt(shippedAt);
        return shipment;
    }

    private static RmaShipmentsView.Row plainRow(Shipment shipment, int number, boolean editable, String labelHref) {
        return new RmaShipmentsView.Row(shipment, number, "ShipmentType." + shipment.getType().name(),
                shipment.getShippedAt() == null ? null : "13.10.2026", null, shipment.getTrackingUrl(), editable, null, null, null, false,
                labelHref, null, null, null, null);
    }

    private static Shipment creation() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()).failed("No funds"));
        return shipment;
    }

    @Test
    void shipmentsCardIsAListWithThePollAndConfirmedActions() {
        // given
        Shipment pickupFailed = typedShipment("21480004", "0000124", null);
        RmaShipmentsView.Row failed = new RmaShipmentsView.Row(creation(), 1, "ShipmentType.Courier", null, null, null, false,
                "order.shipments.state.creation.failed", new Object[]{"No funds"}, "is-warn", false, null,
                "/dashboard/rma/rma-1#pozycje", "/dashboard/rma/rma-1/shipments/creations/cmd-1/remove", null, null);
        RmaShipmentsView.Row pickup = new RmaShipmentsView.Row(pickupFailed, 2, "ShipmentType.Courier", null, null,
                "https://tracking.example/0000124", true,
                "order.shipments.state.pickup.failed", new Object[]{"No courier"}, "is-warn", false, null, null, null,
                "/dashboard/rma/rma-1/shipments/21480004/pickup", null);
        RmaShipmentsView view = new RmaShipmentsView(List.of(failed, pickup), null, "/dashboard/rma/rma-1/shipments/state",
                true, "/dashboard/rma/" + RMA_ID + "/updateShipments", List.of(ShipmentType.values()));

        // when
        String html = render("rma-detail :: body", page(rma(RMAStatus.Processing)), null, new RMAItemsForm(new ArrayList<>()), view);

        // then
        String card = html.substring(html.indexOf("id=\"przesylki\""), html.indexOf("</section>", html.indexOf("id=\"przesylki\"")));
        assertThat(card).contains("class=\"cl-list\"").contains("class=\"cl-list-item\"")
                .contains("data-cl-cancellation-poll=\"/dashboard/rma/rma-1/shipments/state\"")
                .doesNotContain("table is-narrow").doesNotContain("??");
        assertThat(card).containsPattern("<a[^>]*data-cl-confirm[^>]*href=\"/dashboard/rma/rma-1/shipments/creations/cmd-1/remove\"")
                .containsPattern("<a[^>]*data-cl-confirm-tone=\"primary\"[^>]*href=\"/dashboard/rma/rma-1/shipments/21480004/pickup\"");
        // without JavaScript the POST-only actions are plain forms
        assertThat(card).contains("<noscript><form method=\"post\" action=\"/dashboard/rma/rma-1/shipments/creations/cmd-1/remove\">")
                .contains("<noscript><form method=\"post\" action=\"/dashboard/rma/rma-1/shipments/21480004/pickup\">");
        assertThat(card).contains("href=\"/dashboard/rma/rma-1#pozycje\"")
                .contains("href=\"?open=shipment-2\" data-cl-dialog-open=\"shipment-dialog-2\"")
                .doesNotContain("?open=shipment-1\"")
                .contains("href=\"?open=shipment-new\" data-cl-dialog-open=\"shipment-dialog-new\"");
    }

    @Test
    void aShipmentDialogPostsTheOtherShipmentsUnchanged() {
        // given: the first shipment typed in by hand, the second a package, the third still being created
        Shipment first = typedShipment(null, "1111", LocalDateTime.of(2026, 10, 13, 8, 31, 45));
        Shipment second = typedShipment("pkg-2", "2222", null);
        RMA rma = rma(RMAStatus.Processing);
        rma.setShipments(new ArrayList<>(List.of(first, second, creation())));
        RmaDetailPage page = factory.page("store-1", rma, List.of(), List.of(), null, false, CLIENT_LINK, "shipment-2",
                null, Locale.ENGLISH);
        RmaShipmentsView view = shipments(false, plainRow(first, 1, true, null), plainRow(second, 2, true, null),
                plainRow(rma.getShipments().get(2), 3, false, null));

        // when
        String html = render("rma-detail :: body", page, null, new RMAItemsForm(new ArrayList<>()), view);

        // then
        String dialog = dialog(html, "shipment-dialog-2");
        assertThat(dialog).contains("open=\"open\"")
                .contains("action=\"/dashboard/rma/" + RMA_ID + "/updateShipments\"")
                .contains("<input type=\"hidden\" name=\"shipments[0].trackingNo\" value=\"1111\">")
                .contains("<input type=\"hidden\" name=\"shipments[0].carrier\" value=\"DPD\">")
                .contains("<input type=\"hidden\" name=\"shipments[0].type\" value=\"Courier\">")
                .contains("<input type=\"hidden\" name=\"shipments[0].shippedAt\" value=\"2026-10-13T08:31\">")
                .contains("<input type=\"hidden\" name=\"shownPackages\" value=\"pkg-2\">")
                .contains("<input type=\"hidden\" name=\"shipments[1].externalId\" value=\"pkg-2\">")
                .containsPattern("<input class=\"cl-input\"[^>]*name=\"shipments\\[1\\]\\.trackingNo\"[^>]*value=\"2222\"")
                .doesNotContain("shipments[2].");
        assertThat(inputs(dialog, "shipments\\[1\\]\\.trackingNo")).hasSize(1);
        assertThat(dialog(html, "shipment-dialog-1")).doesNotContain("open=\"open\"")
                .contains("<input type=\"hidden\" name=\"shipments[1].trackingNo\" value=\"2222\">");
        assertThat(html).doesNotContain("id=\"shipment-dialog-3\"");
        String added = dialog(html, "shipment-dialog-new");
        assertThat(added).contains("name=\"shipments[3].trackingNo\"")
                .contains("<input type=\"hidden\" name=\"shipments[0].trackingNo\" value=\"1111\">")
                .contains("<input type=\"hidden\" name=\"shipments[1].trackingNo\" value=\"2222\">")
                .doesNotContain("shipments[2].");
    }

    @Test
    void aClosedRmaKeepsTheLabelButNoShipmentMenus() {
        // given
        Shipment sent = typedShipment("pkg-1", "1111", LocalDateTime.of(2026, 10, 13, 8, 31));
        RmaShipmentsView view = shipments(true, plainRow(sent, 1, false,
                "/dashboard/shipping/labels/furgonetka/pkg-1?back=/dashboard/rma/" + RMA_ID));

        // when
        String html = render("rma-detail :: body", page(rma(RMAStatus.Completed)), null, new RMAItemsForm(new ArrayList<>()), view);

        // then
        assertThat(html).contains("href=\"/dashboard/shipping/labels/furgonetka/pkg-1?back=/dashboard/rma/" + RMA_ID + "\"")
                .doesNotContain("shipment-menu-").doesNotContain("shipment-dialog")
                .doesNotContain("?open=shipment-new").doesNotContain("address-dialog");
    }

    @Test
    void addressCardOpensTheAddressFormInADialogAndLinksThePageWithoutJs() {
        // given
        RMA rma = rma(RMAStatus.Processing);
        rma.getShippingDetails().setStreetAndNumber("Main 1");
        rma.getShippingDetails().setPostalCode("60-243");
        rma.getShippingDetails().setCity("Poznan");
        rma.getShippingDetails().setPhone("+48 601 234 567");

        // when
        String html = render("rma-detail :: body", page(rma), null);

        // then
        assertThat(html).containsPattern("<a class=\"cl-link-button\" href=\"/dashboard/rma/" + RMA_ID
                + "/shipping\" data-cl-dialog-open=\"address-dialog\">Edit</a>");
        assertThat(html).contains("<dd class=\"cl-kv-text\"><span>Jan Kowalski</span><br><span>Main 1</span><br><span>60-243 Poznan</span></dd>")
                .contains("+48 601 234 567");
        String dialog = dialog(html, "address-dialog");
        assertThat(dialog).contains("class=\"cl-dialog is-form is-wide\"").contains("id=\"rma-address-form\"")
                .contains("action=\"/dashboard/rma/" + RMA_ID + "/updateShippingDetails\"")
                .contains("class=\"cl-dialog-title\"").contains("data-cl-dialog-close").doesNotContain("cl-card-footer")
                // the page form's 760 px cap would leave the wide dialog half empty
                .doesNotContain("class=\"cl-form\"");
        assertThat(html).doesNotContain("LEGACY-SHIPPING");
    }

    @Test
    void aMissingAddressShowsTheWarning() {
        // given
        RMA missing = rma(RMAStatus.Processing);
        missing.setShippingDetails(null);
        RMA incomplete = rma(RMAStatus.Processing);

        // when
        String none = render("rma-detail :: body", page(missing), null);
        String partial = render("rma-detail :: body", page(incomplete), null);
        String closed = render("rma-detail :: body", page(rma(RMAStatus.Completed)), null);

        // then
        assertThat(none).contains("cl-alert is-warn").contains("No address. Without it you cannot ship the items to the customer.")
                .contains("data-cl-dialog-open=\"address-dialog\">Fill in</a>");
        assertThat(partial).contains("The address is incomplete. Without a complete address you cannot ship the items to the customer.")
                .contains("<span>Jan Kowalski</span>");
        assertThat(closed).doesNotContain("data-cl-dialog-open=\"address-dialog\"").doesNotContain("id=\"address-dialog\"");
    }

    private static String textarea(String html, String name) {
        Matcher matcher = Pattern.compile("<textarea[^>]*name=\"" + name + "\"[^>]*>").matcher(html);
        assertThat(matcher.find()).as(name).isTrue();
        return matcher.group();
    }

    private static String dialog(String html, String id) {
        Matcher matcher = Pattern.compile("(?s)<dialog[^>]*id=\"" + id + "\".*?</dialog>").matcher(html);
        assertThat(matcher.find()).as(id).isTrue();
        return matcher.group();
    }

    private static List<String> inputs(String html, String name) {
        Matcher matcher = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*>").matcher(html);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
