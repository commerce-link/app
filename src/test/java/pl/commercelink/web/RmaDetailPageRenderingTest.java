package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.DefaultTemplateResolver;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.MarketplaceDecision;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.web.rma.RmaDetailPage;
import pl.commercelink.web.rma.RmaDetailPageFactory;
import pl.commercelink.web.rma.RmaNotice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
            <div th:fragment="items">LEGACY-ITEMS</div>
            <div th:fragment="shipping">LEGACY-SHIPPING</div>
            <div th:fragment="shipments">LEGACY-SHIPMENTS</div>
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

    private static String render(String template, RmaDetailPage page, RmaNotice notice) {
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
        assertThat(withItemsNotice.indexOf("ITEMS-NOTICE")).isGreaterThan(withItemsNotice.indexOf("cl-layout-aside"))
                .isLessThan(withItemsNotice.indexOf("LEGACY-ITEMS"));
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
        assertThat(html).contains("LEGACY-DETAILS").contains("LEGACY-EVENTS").contains("LEGACY-ITEMS")
                .contains("LEGACY-SHIPPING").contains("LEGACY-SHIPMENTS");
    }
}
