package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.StoreCopyForm;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCopyTemplateRenderingTest {

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private String render(Map<String, String> errors, String formError, String back) {
        Store source = new Store();
        source.setStoreId("srcstore01");
        source.setName("Madman Gaming");
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("source", source);
        context.setVariable("sourceName", "Madman Gaming");
        context.setVariable("form", StoreCopyForm.of("Madman Gaming (copy)", "abcde12345"));
        context.setVariable("errors", errors);
        context.setVariable("formError", formError);
        context.setVariable("back", back);
        context.setVariable("backHref", back == null ? "/dashboard/store/srcstore01" : back);
        context.setVariable("backLabel", back == null ? "Store Settings" : "Stores");
        return engine.process("store-copy", context);
    }

    @Test
    void pageShowsSourceNameAndScope() {
        // when
        String html = render(Map.of(), null, null);

        // then
        assertThat(html).contains("cl-page-body is-form").contains("href=\"/dashboard/store/srcstore01\"");
        assertThat(html.split("<h1").length - 1).isEqualTo(1);
        assertThat(html).contains(">Madman Gaming<").contains("ID: srcstore01");
        assertThat(html).contains("value=\"Madman Gaming (copy)\"").contains("name=\"newStoreId\" value=\"abcde12345\"");
        assertThat(html).contains("cl-scope-list is-yes").contains("cl-scope-list is-no");
        assertThat(html).contains("Product catalogs with categories and products").contains("Access details and feed files of own suppliers");
        // StoreCopyService copies the warehouse id and the documents switch, not the cost centre or the printers
        assertThat(html).contains("Warehouse: warehouse ID and creating documents").contains("Warehouse cost centre and label printers")
                .doesNotContain("Checkout, RMA and warehouse settings");
        assertThat(html).contains("data-cl-submit-once").contains("data-cl-busy-label=\"Copying…\"").contains("data-cl-busy-note");
        assertThat(html).doesNotContain("class=\"notification").doesNotContain("is-success").doesNotContain("name=\"back\"");
    }

    @Test
    void failedCopyNamesTheStoreThatMayBeIncomplete() {
        // when
        String html = render(Map.of(), "store.copy.error", "/dashboard/stores");

        // then
        assertThat(html).contains("id=\"store-copy-errors\"").contains("Store &quot;Madman Gaming (copy)&quot; may have been created incomplete");
        assertThat(html).contains("name=\"back\" value=\"/dashboard/stores\"");
    }

    @Test
    void blankNameIsMarkedAtTheField() {
        // when
        String html = render(Map.of("newStoreName", "store.copy.error.name.required"), null, null);

        // then
        assertThat(html).contains("href=\"#newStoreName\"").contains("aria-invalid=\"true\"");
    }
}
