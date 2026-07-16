package pl.commercelink.offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDetailsViewTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCategoryIdWithoutCategoryAndGroup() throws Exception {
        // given
        ProductDetailsView view = new ProductDetailsView(
                "cat-1", "pim-1", "MFN-1", "Intel", "Core", "Intel Core i7");

        // when
        String json = objectMapper.writeValueAsString(view);

        // then
        assertThat(json).contains("\"categoryId\":\"cat-1\"");
        assertThat(json).doesNotContain("\"category\":");
        assertThat(json).doesNotContain("\"group\":");
    }
}
