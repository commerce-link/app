package pl.commercelink.offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDetailsViewTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCategoryAsPlainString() throws Exception {
        // given
        ProductDetailsView view = new ProductDetailsView(
                "cat-1", "pim-1", "MFN-1", "Intel", "Core", "Intel Core i7", null, "CPU");

        // when
        String json = objectMapper.writeValueAsString(view);

        // then
        assertThat(json).contains("\"category\":\"CPU\"");
    }

    @Test
    void serializesNonEnumCategoryValue() throws Exception {
        // given
        ProductDetailsView view = new ProductDetailsView(
                "cat-1", "pim-1", "MFN-1", "Asus", "ROG", "Asus ROG Strix", null, "Graphics Cards");

        // when
        String json = objectMapper.writeValueAsString(view);

        // then
        assertThat(json).contains("\"category\":\"Graphics Cards\"");
    }
}
