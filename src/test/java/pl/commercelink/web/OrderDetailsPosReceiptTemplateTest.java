package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDetailsPosReceiptTemplateTest {

    @Test
    void hiddenDecisionFieldsAreDisabledSoTheyNeitherBlockNorReachTheSubmit() throws Exception {
        // given
        String html = Files.readString(Path.of("src/main/resources/templates/orderDetails.html"), StandardCharsets.UTF_8);

        // when
        int box = html.indexOf("id=\"pos-receipt-decision\"");
        String script = html.substring(html.indexOf("<script", box), html.indexOf("</script>", box));

        // then: a bad e-mail typed before switching to the cash register must not fail browser validation silently
        assertThat(script).contains("input.disabled = !on");
        assertThat(script).contains("input.required = on");
    }
}
