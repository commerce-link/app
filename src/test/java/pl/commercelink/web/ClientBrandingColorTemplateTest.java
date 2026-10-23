package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Branding;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ClientBrandingColorTemplateTest {

    private static final Pattern STYLE_WITH_COLOR = Pattern.compile("th:style(?:append)?=\"([^\"]*safePrimaryColor[^\"]*)\"");

    @Test
    void returnPageWritesOnlyAValidatedBrandColourIntoStyles() throws Exception {
        // given
        String html = Files.readString(Path.of("src/main/resources/templates", "client-return.html"), StandardCharsets.UTF_8);

        // when
        Matcher styles = STYLE_WITH_COLOR.matcher(html);

        // then
        assertThat(html).doesNotContain("branding.primaryColor");
        int count = 0;
        while (styles.find()) {
            count++;
            assertThat(styles.group(1)).as("style without an empty-colour guard: %s", styles.group(1))
                    .startsWith("${#strings.isEmpty(branding.safePrimaryColor)} ? null : ");
        }
        assertThat(count).isPositive();
    }

    @Test
    void offerPageTakesTheBrandColourOnlyFromThePublicFrame() throws Exception {
        // when / then
        for (Path source : ClientOfferTemplateRenderingTest.templateSources()) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8)).as(source.toString())
                    .doesNotContain("branding")
                    .doesNotContain("style");
        }
    }

    @Test
    void orderPageTakesTheBrandColourOnlyFromThePublicFrame() throws Exception {
        // when / then
        assertThat(Files.readString(Path.of("src/main/resources/templates/clientOrder.html"), StandardCharsets.UTF_8))
                .contains("layout:decorate=\"~{public-layout}\"");
        for (Path source : ClientOrderTemplateRenderingTest.templateSources()) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8)).as(source.toString())
                    .doesNotContain("branding")
                    .doesNotContain("style");
        }
    }

    @Test
    void orderPageWritesTheNormalisedBrandColourOnTheBody() {
        // given
        ClientOrderRenderer renderer = ClientOrderRenderer.newOrder();
        Branding branding = new Branding();
        branding.setPrimaryColor("#2277EE");
        renderer.store().setBranding(branding);

        // when
        String html = renderer.render();

        // then
        assertThat(html).containsPattern("<body class=\"cl-public\" style=\"--cl-brand:#2277ee;[^\"]*\"");
    }

    @Test
    void offerPageWritesTheNormalisedBrandColourOnTheBody() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        Branding branding = new Branding();
        branding.setPrimaryColor("#2277EE");
        renderer.store().setBranding(branding);

        // when
        String html = renderer.render();

        // then
        assertThat(html).containsPattern("<body class=\"cl-public\" style=\"--cl-brand:#2277ee;[^\"]*\"");
    }

    @Test
    void maliciousBrandColourNeverReachesTheOfferPage() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        Branding branding = new Branding();
        branding.setPrimaryColor("red;}</style><script>alert(1)</script>");
        renderer.store().setBranding(branding);

        // when
        String html = renderer.render();

        // then
        assertThat(html).doesNotContain("alert(1)").doesNotContain("red;}");
    }
}
