package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the fulfilment queue styles: the card-mode row of every part of an order (the orders card rules reorder some of
 * them), the wrapping count header that leaves the customer column room beside the side column, and the even spacing
 * of the narrowing options from 1024 px.
 */
class FulfilmentQueueStylesContractTest {

    private static String queueSection() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int section = css.indexOf("/* Fulfilment queue (/dashboard/fulfilment/queue");
        assertThat(section).as("the fulfilment queue block").isPositive();
        return css.substring(section);
    }

    private static String ruleBody(String css, String head) {
        int rule = css.indexOf(head + " {");
        assertThat(rule).as(head).isNotNegative();
        return css.substring(rule, css.indexOf('}', rule));
    }

    private static String cardBlock() throws Exception {
        String css = queueSection();
        int media = css.indexOf("@media screen and (max-width: 719px)");
        assertThat(media).as("the queue's card block").isPositive();
        return css.substring(media);
    }

    private static void assertRow(String block, String selector, int row) {
        String head = ".cl-page .cl-table.is-queue " + selector + " {";
        int rule = block.indexOf(head);
        assertThat(rule).as(selector).isNotNegative();
        assertThat(block.substring(rule, block.indexOf('}', rule))).contains("grid-row: " + row + ";");
    }

    @Test
    void cardPartsSitInTheSpecifiedRows() throws Exception {
        // given
        String block = cardBlock();

        // when / then
        assertRow(block, ".cl-row-link", 1);
        assertRow(block, ".cl-cell-count", 1);
        assertRow(block, ".cl-table-key .cl-table-sub", 2);
        assertRow(block, ".cl-cell-client", 3);
        assertRow(block, ".cl-cell-ordered", 4);
        assertRow(block, ".cl-cell-due-date", 5);
        assertRow(block, ".cl-due-note", 6);
        assertThat(block).contains("grid-row: 1 / span 6;");
    }

    @Test
    void countHeaderWrapsWhileTheValueStaysOnOneLine() throws Exception {
        // given
        String css = queueSection();

        // when
        String header = ruleBody(css, ".cl-page .cl-table.is-queue thead th.is-numeric");
        String value = ruleBody(css, ".cl-page .cl-table.is-queue .cl-cell-count");

        // then
        assertThat(header).contains("white-space: normal;");
        assertThat(value).contains("white-space: nowrap;");
    }

    @Test
    void narrowingOptionsTakeTheirOwnHeightFromDesktopWidth() throws Exception {
        // given
        String css = queueSection();
        int media = css.indexOf("@media screen and (min-width: 1024px) {\n    .cl-page .cl-check-stack .cl-check {");
        assertThat(media).as("the narrowing options' desktop block").isNotNegative();

        // when
        String rule = ruleBody(css.substring(media), ".cl-page .cl-check-stack .cl-check");

        // then
        assertThat(rule).contains("min-height: 0;");
        assertThat(css.substring(0, media)).doesNotContain("cl-check-stack .cl-check {\n    min-height");
    }

    @Test
    void theRowLinkOverlayIsClippedToItsRowOutsideCardMode() throws Exception {
        // given
        String css = queueSection();
        int media = css.indexOf("@media screen and (min-width: 720px) {\n    .cl-page .cl-table.is-queue tbody tr {");
        assertThat(media).as("the row clip block, outside the card mode").isNotNegative();

        // when
        String rule = ruleBody(css.substring(media), ".cl-page .cl-table.is-queue tbody tr");

        // then
        assertThat(rule).contains("clip-path: inset(0);");
    }

    @Test
    void aNarrowCardFoldsTheOrderedColumnUnderTheKeyOnlyOutsideCardMode() throws Exception {
        // given
        String css = queueSection();

        // when
        String container = ruleBody(css, ".cl-page .cl-queue-card");
        int media = css.indexOf("@media screen and (min-width: 720px) {\n    @container (width < 660px) {");
        String hiddenByDefault = ruleBody(css, ".cl-page .cl-table.is-queue .cl-table-key .cl-table-sub.cl-queue-ordered-sub");

        // then
        assertThat(container).contains("container-type: inline-size;");
        assertThat(hiddenByDefault).contains("display: none;");
        assertThat(media).as("the narrow-card block, outside the card mode").isNotNegative();
        String narrow = css.substring(media, css.indexOf("\n}\n", media));
        assertThat(ruleBody(narrow, ".cl-page .cl-table.is-queue .is-secondary-column")).contains("display: none;");
        assertThat(ruleBody(narrow, ".cl-page .cl-table.is-queue .cl-table-key .cl-table-sub.cl-queue-ordered-sub")).contains("display: block;");
        assertThat(ruleBody(narrow, ".cl-page .cl-table.is-queue .cl-queue-ordered-sub .cl-queue-label")).contains("display: inline;");
    }
}
