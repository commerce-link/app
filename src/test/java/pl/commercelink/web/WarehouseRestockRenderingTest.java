package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.warehouse.builtin.RestockForm;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseRestockRenderingTest {

    @Test
    void priceLimitIsOptionalAndCategoriesFollowTheCatalog() {
        // given
        ProductCatalog catalog = new ProductCatalog();
        catalog.setCatalogId("c1");
        catalog.setName("Komputery");
        Context context = new Context();
        context.setVariable("restock", new RestockForm(List.of(catalog),
                Map.of("c1", List.of(Map.of("id", "k1", "name", "GPU"))), "c1", null));

        // when
        String html = EnglishFragmentTemplateEngine.create()
                .process("<form th:insert=\"~{fragments/warehouse-restock :: fields}\"></form>", context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).containsPattern("<select[^>]*name=\"restockPrice\"(?![^>]*required)[^>]*>");
        assertThat(html).contains("<option value=\"\">No limit</option>");
        assertThat(html).contains("data-cl-options-for=\"restock-catalog\"").contains("data-parent=\"c1\"");
        assertThat(html).containsPattern("<option[^>]*value=\"c1\"[^>]*selected");
    }
}
