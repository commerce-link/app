package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the card-mode row of every part of a fulfilment queue order, since the orders card rules reorder some of them. */
class FulfilmentQueueStylesContractTest {

    private static String cardBlock() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int section = css.indexOf("/* Fulfilment queue (/dashboard/fulfilment/queue");
        assertThat(section).as("the fulfilment queue block").isPositive();
        int media = css.indexOf("@media screen and (max-width: 719px)", section);
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
}
