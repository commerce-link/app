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
    void theScriptReadsFormAttributesNotPropertiesAFieldCanShadow() throws Exception {
        // given: Payments' search and menu carry fields named "method" (the payment-method filter), and a field's name
        // shadows the form's property of the same name (form.method became the input and the script threw)
        String script = read("src/main/resources/static/js/list-page.js");
        String payments = read("src/main/resources/templates/payments.html");

        // then
        assertThat(payments).contains("name=\"method\"");
        assertThat(script).doesNotContainPattern("form\\.(method|action)\\b");
    }

    @Test
    void anOutcomeNoticeRenderedWithThePageIsFocusedOnce() throws Exception {
        // given
        String script = read("src/main/resources/static/js/list-page.js");
        String offers = read("src/main/resources/templates/offers.html");

        // then
        assertThat(script).contains("root.querySelector('[data-cl-list-notice]')").contains("notice.focus()");
        assertThat(offers).contains("tabindex=\"-1\" data-cl-list-notice");
    }

    @Test
    void aLinkBackToTheListFollowsTheListAfterAnInPlaceChange() throws Exception {
        // given: "Zamów odbiór" in the orders list header sits outside the swapped block
        String script = read("src/main/resources/static/js/list-page.js");
        String orders = read("src/main/resources/templates/orders/list.html");

        // then
        assertThat(script).contains("document.querySelectorAll('a[data-cl-list-back]')")
                .contains("url.searchParams.set('back', target.pathname + target.search)")
                .contains("syncBackLinks(target);");
        assertThat(orders).contains("data-cl-list-back");
    }
}
