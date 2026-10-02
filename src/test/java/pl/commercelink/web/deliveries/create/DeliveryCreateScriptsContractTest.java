package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCreateScriptsContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void createPagesCarryNoInlineBehaviourNoBulmaLookAndLoadTheirScripts() throws Exception {
        for (String template : List.of("items", "purchase", "manual", "parts")) {
            String html = read("src/main/resources/templates/deliveries/create/" + template + ".html");
            assertThat(html).as(template)
                    .doesNotContain("style=\"").doesNotContain("<style").doesNotContain("onclick=")
                    .doesNotContain("onchange=").doesNotContain("oninput=").doesNotContain("onsubmit=")
                    .doesNotContain("<script>").doesNotContain("th:utext")
                    .doesNotContain("class=\"button").doesNotContain("class=\"box").doesNotContain("class=\"columns")
                    .doesNotContain("class=\"notification").doesNotContain("class=\"tag").doesNotContain("class=\"select")
                    .doesNotContain("class=\"input").doesNotContain("class=\"table").doesNotContain("class=\"level");
            assertThat(html).as(template + ": th:if beside th:replace").doesNotContainPattern("th:if=\"[^\"]*\"[^>]*th:replace")
                    .doesNotContainPattern("th:replace=\"[^\"]*\"[^>]*th:if=");
        }
        for (String script : List.of("delivery-items.js", "delivery-purchase.js", "delivery-manual.js", "delivery-fulfilment.js")) {
            String js = read("src/main/resources/static/js/" + script);
            assertThat(js).as(script).contains("'use strict'").doesNotContain("innerHTML").doesNotContain(".style.");
        }
    }

    @Test
    void submitButtonsDisableThemselvesAndComeBackFromTheBackForwardCache() throws Exception {
        // when
        String purchase = read("src/main/resources/static/js/delivery-purchase.js");
        String manual = read("src/main/resources/static/js/delivery-manual.js");

        // then
        assertThat(purchase).contains("event.submitter && event.submitter.id === 'purchase-confirm-submit'")
                .contains("addEventListener('pageshow'").contains("event.persisted").contains("event.key === 'Enter'")
                .contains("addressList.scrollTop").doesNotContain("scrollIntoView");
        assertThat(manual).contains("event.submitter && event.submitter.id === 'save-button'")
                .contains("addEventListener('pageshow'");
    }

    @Test
    void stepOneKeepsEnterInsideTheTableAndFollowsTheTicks() throws Exception {
        // when
        String js = read("src/main/resources/static/js/delivery-items.js");

        // then
        assertThat(js).contains("event.key === 'Enter'").contains("data-cl-source-type")
                .contains("data-cl-remove-unselected").contains("release-label").contains("empty-help")
                .contains("getClientRects().length > 0");
    }

    @Test
    void costFieldsTakeNoMoreThanTwoDecimals() throws Exception {
        // when
        String js = read("src/main/resources/static/js/delivery-items.js");

        // then
        assertThat(js).contains("limitToCents").contains("input[data-cl-unit-cost], #fulfilment-cost");
    }

    @Test
    void suggestionsAreFetchedAfterThePageAndKeepWhatWasTyped() throws Exception {
        // when
        String js = read("src/main/resources/static/js/delivery-items.js");

        // then
        assertThat(js).contains("[data-cl-suggestions]").contains("data-cl-suggestion-list")
                .contains("data-cl-suggestions-retry").contains("suggestedItems[' + index + ']")
                .contains("section.hidden = all.length === 0").contains("data-summary-failed");
    }

    @Test
    void tickingAnOrderSourceBackRaisesTheQuantityByItsPiecesAsUntickingLoweredIt() throws Exception {
        // when
        String js = read("src/main/resources/static/js/delivery-items.js");

        // then: untick lowers the quantity by the source's pieces, tick adds them back (not only up to the minimum,
        // which would leave a negative warehouse adjustment after untick + tick)
        assertThat(js).contains("checkbox.checked ? Math.max(current + qty, min) : Math.max(min, current - qty)")
                .doesNotContain("Math.max(current, min)");
    }

    @Test
    void openDeliveryRowKeepsItsCodesReadableOnTheTintedSurface() throws Exception {
        // when
        String css = read("src/main/resources/static/css/commercelink.css");
        String block = css.substring(css.indexOf("/* --- New delivery"));

        // then: --cl-ink-3 on --cl-surface-2 is 4.47:1, below AA for the 12.5 px EAN and code line
        assertThat(block).containsPattern(
                "\\.cl-table\\.is-delivery tr\\.cl-row-main\\.is-open \\.cl-table-sub \\{\\s*color: var\\(--cl-ink-2\\);");
    }

    @Test
    void sourceLinksAreTouchTargetsBelowDesktop() throws Exception {
        // when
        String css = read("src/main/resources/static/css/commercelink.css");
        String block = css.substring(css.indexOf("/* --- New delivery"));
        String touch = block.substring(block.indexOf("@media screen and (max-width: 1023px)"));

        // then: the link to a source sits inside the 44 px checkbox label, and is a target of its own
        assertThat(touch).containsPattern("\\.cl-table\\.is-delivery \\.cl-subtable \\.cl-check a \\{[^}]*min-height: 44px;");
    }

    @Test
    void stepPagesMoveTheFocusToTheErrorSummary() throws Exception {
        // then: a page answered with errors puts the keyboard and screen reader on the summary (spec 5.2)
        for (String script : List.of("delivery-items.js", "delivery-manual.js", "delivery-purchase.js")) {
            assertThat(read("src/main/resources/static/js/" + script)).as(script)
                    .contains("document.querySelector('[data-cl-error-summary]')").contains("summary.focus()");
        }
    }

    @Test
    void stylesAddOnlyTheProposedPiecesWithinTheAllowedBreakpoints() throws Exception {
        // when
        String css = read("src/main/resources/static/css/commercelink.css");
        String block = css.substring(css.indexOf("/* --- New delivery"));

        // then
        assertThat(block).contains(".cl-layout-side.is-sticky").contains(".cl-card-footer.is-stacked")
                .contains(".cl-table.is-delivery").contains(".cl-choice-group.is-scroll")
                .doesNotContain("--cl-ok:").doesNotContain("#00d1b2");
        assertThat(block.replaceAll("@media screen and \\((max|min)-width: (719|720|1023|1024|1215|1216|1365|1366)px\\)", ""))
                .doesNotContain("@media screen and (max-width").doesNotContain("@media screen and (min-width");
    }
}
