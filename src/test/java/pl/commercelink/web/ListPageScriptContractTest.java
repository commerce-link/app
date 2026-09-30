package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListPageScriptContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void scriptKnowsNoPageOfItsOwn() throws Exception {
        // given
        String script = read("src/main/resources/static/js/list-page.js");

        // then
        assertThat(script).contains("'use strict';").doesNotContain("/dashboard/orders").doesNotContain("/dashboard/deliveries")
                .doesNotContain("data-cl-orders").doesNotContain("innerHTML").doesNotContain("alert(")
                .doesNotContain("confirm(").doesNotContain("prompt(");
    }

    @Test
    void everyHookTheScriptUsesIsRenderedByEveryList() throws Exception {
        // given
        String script = read("src/main/resources/static/js/list-page.js");
        String pagination = read("src/main/resources/templates/fragments/pagination.html");
        String orders = read("src/main/resources/templates/orders/list.html") + pagination;
        String deliveries = read("src/main/resources/templates/deliveries.html") + pagination;

        // then
        for (String hook : List.of("data-cl-list-results", "data-cl-list-path", "data-cl-list-fragment", "data-cl-list-nav",
                "data-cl-list-form", "data-cl-filter-menu", "data-cl-autosubmit")) {
            assertThat(script).as("script uses " + hook).contains(hook);
            assertThat(orders).as("orders list renders " + hook).contains(hook);
            assertThat(deliveries).as("deliveries list renders " + hook).contains(hook);
        }
        assertThat(deliveries).contains("data-cl-toolbar-toggle").contains("data-cl-autosubmit-hide").contains("/js/list-page.js");
        assertThat(orders).doesNotContain("data-cl-" + "orders-").contains("/js/list-page.js").doesNotContain("orders-" + "list.js");
    }

    @Test
    void scriptKeepsTheBehavioursTheOrdersListRelyOn() throws Exception {
        // given
        String script = read("src/main/resources/static/js/list-page.js");

        // then
        assertThat(script).contains("data-cl-autosubmit-hide").contains("data-cl-search-had").contains(".cl-table-results")
                .contains(".cl-table-search").contains("popstate").contains("pushState").contains("replaceState")
                .contains("X-Requested-With").contains("DOMParser").contains("aria-busy").contains("'Escape'")
                .contains("details[data-cl-filter-menu][open]").contains("window.location.assign")
                .contains("data-cl-toolbar-toggle").contains("is-collapsed").contains("aria-expanded")
                .contains("matchMedia('(max-width: 719px)')");
    }
}
