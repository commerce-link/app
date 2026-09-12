package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders fragments/schedule-field.html through a real Thymeleaf engine. The script fragment
 * inlines message keys, so a missing key or a broken inline expression would only show up at
 * runtime otherwise -- the builder would silently summarise every schedule as an empty string.
 */
class ScheduleFieldFragmentTest {

    private String render(String selector) {
        return EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{fragments/schedule-field :: " + selector + "}\"></div>", new Context());
    }

    @Test
    void theInputFragmentSubmitsUnderTheNameTheSupplierFormExpects() {
        // when
        String html = render("input('feedSchedule', '0 5 * * ? *', 5)");

        // then
        assertThat(html).contains("name=\"feedSchedule\"");
        assertThat(html).contains("value=\"0 5 * * ? *\"");
        assertThat(html).contains("data-min-interval=\"5\"");
    }

    @Test
    void theInputFragmentResolvesEveryMessageItUses() {
        // when
        String html = render("input('feedSchedule', '', 5)");

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Hours");
        assertThat(html).contains("Advanced: cron expression");
        // the minimum interval reaches the help text as a message argument, not as literal {0}
        assertThat(html).contains("5 minutes apart");
    }

    @Test
    void theInputFragmentOffersThreeModesAndNoConflictWarning() {
        // when
        String html = render("input('feedSchedule', '', 5)");

        // then
        assertThat(html).contains("data-mode=\"default\"");
        assertThat(html).contains("data-mode=\"every\"");
        assertThat(html).contains("data-mode=\"at\"");
        // "at" schedules pick hours plus one minute, so the unrepresentable combination the old
        // builder warned about cannot be reached any more
        assertThat(html).doesNotContain("schedule-exact-conflict");
    }

    @Test
    void theScriptFragmentInlinesTheSameSummaryTextsTheTableCellUses() {
        // when
        String html = render("script");

        // then -- javascript inlining escapes non-ASCII, so the em dash arrives as —
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Default \\u2014 once a night");
        assertThat(html).contains("Every {0} min");
        assertThat(html).contains("On weekdays at {0}");
        assertThat(html).contains("window.scheduleField");
    }
}
