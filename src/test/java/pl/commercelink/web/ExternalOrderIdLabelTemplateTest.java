package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalOrderIdLabelTemplateTest {

    private static final String LABEL = "th:text=\"#{order.external.order.id(${order.externalOrderId})}\"";

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/" + path), StandardCharsets.UTF_8);
    }

    @Test
    void ordersListAndOrderDetailsLabelTheExternalOrderIdWithALocalizedMessage() throws Exception {
        // when
        String list = read("templates/fragments/ordersListFragment.html");
        String details = read("templates/orderDetails.html");

        // then
        assertThat(list).contains(LABEL).doesNotContain("'ext: '");
        assertThat(details).contains(LABEL).doesNotContain("'ext: '");
    }

    @Test
    void externalOrderIdLabelIsTranslatedInBothLanguages() throws Exception {
        // when
        String pl = read("messages_pl.properties");
        String en = read("messages_en.properties");

        // then
        assertThat(pl).contains("\norder.external.order.id=nr zewn. {0}\n");
        assertThat(en).contains("\norder.external.order.id=ext. no {0}\n");
    }
}
