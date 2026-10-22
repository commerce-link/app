package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The public page frame and the client offer styles in commercelink.css. */
class PublicPageStylesContractTest {

    private static String publicCss() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));
        return css.substring(css.indexOf("/* ===== Public page"));
    }

    @Test
    void brandLinkColourReachesOnlyContentAndFooterLinks() throws Exception {
        // given — the skip link sits on the brand ink background and the language switch has its own colours
        String css = publicCss();

        // then
        assertThat(css).contains("body.cl-public :is(.cl-public-main, .cl-public-foot) a:not(.cl-button) { color: var(--cl-brand-ink); }")
                .doesNotContain("body.cl-public a:not(.cl-button)")
                .doesNotContain("body.cl-public a {");
    }

    @Test
    void languageSwitchKeepsItsColoursWithoutImportant() throws Exception {
        // given
        String css = publicCss();
        String lang = css.substring(css.indexOf(".cl-public-lang a, .cl-public-lang span {"), css.indexOf(".cl-public-main {"));

        // then
        assertThat(lang).contains("color: var(--cl-ink-2);")
                .contains(".cl-public-lang [aria-current] { color: var(--cl-ink);")
                .doesNotContain("!important");
    }

    @Test
    void phoneHeaderBarIsFiftySixPixelsHigh() throws Exception {
        // given — 44 px language targets plus 6 + 6 px padding
        String css = publicCss();

        // then
        assertThat(css).contains("  .cl-public-bar { min-height: 56px; padding: 6px 16px; gap: 8px; }")
                .contains("  .cl-public-lang a, .cl-public-lang span { min-width: 44px; min-height: 44px; }");
    }

    @Test
    void consecutiveHelpLinesInTheOfferCardKeepAGap() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains("body.cl-public .cl-card-inset > .cl-help + .cl-help { margin-top: 8px; }");
    }

    @Test
    void sharedPublicComponentsAreScopedToThePublicBody() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains("body.cl-public .cl-step-badge {", "body.cl-public .cl-kv-grid {",
                "body.cl-public dl.cl-kv.is-bank {", "body.cl-public .cl-card-inset > .cl-help + .cl-help { margin-top: 8px; }");
        assertThat(css).doesNotContain(".cl-offer-page .cl-step-badge", ".cl-offer-page .cl-kv-grid", ".cl-offer-page dl.cl-kv.is-bank");
    }

    @Test
    void paymentMethodChoiceUsesTheSurfaceToken() throws Exception {
        // given
        String css = publicCss();
        String offer = css.substring(css.indexOf("/* ===== Client offer"));

        // then
        assertThat(offer).doesNotContain("#fff")
                .contains("cursor: pointer; background: var(--cl-surface); }");
    }

    @Test
    void primaryButtonEdgeComesFromThePalette() throws Exception {
        // given — a pale brand needs a darker edge than its own fill to show the button on the white card
        String css = publicCss();

        // then
        assertThat(css).contains("body.cl-public .cl-button.is-primary { background: var(--cl-brand); border-color: var(--cl-brand-edge);")
                .contains("body.cl-public .cl-button.is-primary:hover { background: var(--cl-brand-hover); border-color: var(--cl-brand-edge-hover);");
    }

    @Test
    void messageCardHasInnerSpacing() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-public-main .cl-card.cl-public-message { padding: 28px 24px; }");
    }

    @Test
    void variantButtonIsHiddenFromTheFirstPaintWhenScriptsRun() throws Exception {
        // given — client-offer.js is deferred behind FontAwesome, so hiding the button from the script alone made it flash
        String css = publicCss();

        // then
        assertThat(css).contains("@media (scripting: enabled) {\n  .cl-offer-page .cl-variant-apply:not([data-cl-revealed]) { display: none; }");
    }

    @Test
    void redirectTargetsKeepAGapToTheTopEdge() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-offer-page .cl-list-item.is-group[id], .cl-offer-page #client-offer-pay-title { scroll-margin-top: 16px; }");
    }

    @Test
    void variantUpdateSpinnerShowsOnlyWhenTheAnswerIsSlow() throws Exception {
        // given — a fast update must not flash the spinner
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-offer-page .cl-offer-updating { display: none; }")
                .contains(".cl-offer-page #client-offer-items.is-slow .cl-offer-updating { display: inline-block; }");
    }

    @Test
    void orderStagesAndFactsAreSharedPublicComponents() throws Exception {
        // given
        String css = publicCss();
        String order = css.substring(css.indexOf("/* ===== Client order"), css.indexOf("/* ===== Public message page"));

        // then
        assertThat(order).contains("body.cl-public ol.cl-stages {", "body.cl-public .cl-stage.is-current .cl-stage-dot {",
                "body.cl-public dl.cl-facts {", ".cl-order-page :is(#delivery, #date, #payment) { scroll-margin-top: 16px; }")
                .doesNotContain("!important")
                .doesNotContain(".cl-offer-page");
    }

    @Test
    void orderStagesTurnIntoAVerticalListOnAPhone() throws Exception {
        // given
        String css = publicCss();
        String order = css.substring(css.indexOf("/* ===== Client order"), css.indexOf("/* ===== Public message page"));
        String phone = order.substring(order.indexOf("@media (max-width: 719px) {"));

        // then
        assertThat(phone).contains("body.cl-public ol.cl-stages { grid-auto-flow: row; grid-template-columns: minmax(0, 1fr); }")
                .contains(".cl-order-page .cl-list-actions .cl-button { width: 100%; justify-content: center; min-height: 44px; }");
    }

    @Test
    void dayTilesShowFocusOnTheTileAndStayTouchSized() throws Exception {
        // given — the radio itself is invisible, so the tile has to carry the focus ring
        String css = publicCss();
        String order = css.substring(css.indexOf("/* ===== Client order"), css.indexOf("/* ===== Public message page"));

        // then
        assertThat(order).contains("body.cl-public label.cl-choice.is-date:has(input:focus-visible) { outline: 2px solid var(--cl-accent);",
                        "body.cl-public label.cl-choice.is-date input { position: absolute; inset: 0;",
                        "body.cl-public dl.cl-amounts {", "  body.cl-public label.cl-choice.is-date { min-height: 52px; }",
                        "  body.cl-public .cl-date-actions .cl-button { min-height: 44px; }");
    }

    @Test
    void noMovedComponentIsStillScopedToTheOfferPage() throws Exception {
        // given
        String css = publicCss();
        List<String> selectors = List.of(".cl-stack", ".cl-card-head.has-step", ".cl-step-badge", ".cl-list .cl-list-item.has-icon",
                ".cl-list-icon", ".cl-list-amount", ".cl-kv-grid", "dl.cl-kv.is-bank", ".cl-help", ".cl-form-grid",
                ".cl-fieldset", ".cl-choice-group.is-row", ".cl-static-value");

        // then
        for (String selector : selectors) {
            assertThat(css).as(selector).doesNotContain(".cl-offer-page " + selector + " ")
                    .doesNotContain(".cl-offer-page " + selector + ",")
                    .doesNotContain(".cl-offer-page " + selector + "{")
                    .contains("body.cl-public " + selector);
        }
    }

    @Test
    void resendLinkReachesATouchTargetOnPhoneAndTablet() throws Exception {
        // given
        String css = publicCss();
        String tablet = css.substring(css.indexOf("@media (max-width: 1023px) {", css.indexOf("/* ===== Client order")));

        // then
        assertThat(tablet).contains(".cl-order-page .cl-help .cl-link-button { min-height: 44px; margin-block: -14px; }");
    }

    @Test
    void dayTileHoverNeedsAHoverDevice() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains("@media (hover: hover) {\n  body.cl-public label.cl-choice.is-date:hover {")
                .doesNotContain("\nbody.cl-public label.cl-choice.is-date:hover");
    }
}
