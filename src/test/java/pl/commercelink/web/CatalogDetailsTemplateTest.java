package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogDetailsTemplateTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/catalogDetails.html");

    private static String template() throws Exception {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void theCatalogFormInitialisesTheScheduleBuilderAfterLoadingItsScript() throws Exception {
        // given
        String html = template();

        // then
        assertThat(html).contains("fragments/schedule-field :: input('pricelistSchedule', ${productCatalog.pricelistSchedule}, "
                + "${scheduleMinIntervalMinutes}, #{catalog.pricelist.schedule.summary.default}, 'minutes,hours,days')");
        assertThat(html).contains("window.scheduleField.init(field)");
        assertThat(html.indexOf("schedule-field :: script")).isLessThan(html.indexOf("window.scheduleField.init("));
        assertThat(html.indexOf("id=\"editCatalogForm\"")).isLessThan(html.indexOf("schedule-field :: input("));
    }

    @Test
    void theFormRefusesToSubmitAnIncompleteExactTimeSchedule() throws Exception {
        // given
        String html = template();

        // then
        assertThat(html).contains("form.addEventListener('submit'");
        assertThat(html).contains("if (window.scheduleField.valid(field)) { return; }");
        assertThat(html).contains("event.preventDefault();");
        assertThat(html).contains("#{schedule.at.nohours}");
        assertThat(html).contains("id=\"pricelist-schedule-error\"");
    }

    @Test
    void theCatalogDefaultTextResolvesInTheBuilder() {
        // when
        String html = EnglishFragmentTemplateEngine.create().process(
                "<div th:replace=\"~{fragments/schedule-field :: input('pricelistSchedule', '', 5, "
                        + "#{catalog.pricelist.schedule.summary.default}, 'minutes,hours,days')}\"></div>", new Context());

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-default-text=\"Default — once a day, at a random hour at night\"");
        assertThat(html).contains("data-units=\"minutes,hours,days\"");
    }
}
