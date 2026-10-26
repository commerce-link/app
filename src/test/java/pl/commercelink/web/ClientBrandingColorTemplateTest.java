package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Branding;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClientBrandingColorTemplateTest {

    @Test
    void returnPageTakesTheBrandColourOnlyFromThePublicFrame() throws Exception {
        // when / then
        assertThat(Files.readString(Path.of("src/main/resources/templates/client-return.html"), StandardCharsets.UTF_8))
                .contains("layout:decorate=\"~{public-layout}\"");
        for (Path source : ClientReturnTemplateRenderingTest.templateSources()) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8)).as(source.toString())
                    .doesNotContain("branding")
                    .doesNotContain("style");
        }
    }

    @Test
    void returnPageWritesTheNormalisedBrandColourOnTheBody() {
        // given
        ClientReturnRenderer renderer = ClientReturnRenderer.approved();
        Branding branding = new Branding();
        branding.setPrimaryColor("#2277EE");
        renderer.store().setBranding(branding);

        // when
        String html = renderer.render();

        // then
        assertThat(html).containsPattern("<body class=\"cl-public\" style=\"--cl-brand:#2277ee;[^\"]*\"");
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
        assertThat(html).containsPattern("<body class=\"cl-public is-split\" style=\"--cl-brand:#2277ee;[^\"]*\"");
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
        assertThat(html).containsPattern("<body class=\"cl-public is-split\" style=\"--cl-brand:#2277ee;[^\"]*\"");
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
