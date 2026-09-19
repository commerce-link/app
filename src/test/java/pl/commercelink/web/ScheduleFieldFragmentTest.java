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
        String html = render("input('feedSchedule', '0 5 * * ? *', 5, #{store.supplier.schedule.summary.default}, 'minutes,hours,days')");

        // then
        assertThat(html).contains("name=\"feedSchedule\"");
        assertThat(html).contains("value=\"0 5 * * ? *\"");
        assertThat(html).contains("data-min-interval=\"5\"");
    }

    @Test
    void theInputFragmentResolvesEveryMessageItUses() {
        // when
        String html = render("input('feedSchedule', '', 5, #{store.supplier.schedule.summary.default}, 'minutes,hours,days')");

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Hours");
    }

    @Test
    void theExpressionTravelsInAHiddenFieldWithNoCronInputForTheOperator() {
        // the builder is the only way to set a schedule; a raw cron field was dropped on purpose
        String html = render("input('feedSchedule', '0 5 * * ? *', 5, #{store.supplier.schedule.summary.default}, 'minutes,hours,days')");

        // then
        assertThat(html).containsPattern("<input type=\"hidden\" class=\"schedule-input\"[^>]*name=\"feedSchedule\"");
        assertThat(html).doesNotContain("type=\"text\"");
        assertThat(html).doesNotContain("schedule-advanced");
    }

    @Test
    void theInputFragmentOffersThreeModesAndNoConflictWarning() {
        // when
        String html = render("input('feedSchedule', '', 5, #{store.supplier.schedule.summary.default}, 'minutes,hours,days')");

        // then
        assertThat(html).contains("data-mode=\"default\"");
        assertThat(html).contains("data-mode=\"every\"");
        assertThat(html).contains("data-mode=\"at\"");
        // "at" schedules pick hours plus one minute, so the unrepresentable combination the old
        // builder warned about cannot be reached any more
        assertThat(html).doesNotContain("schedule-exact-conflict");
    }

    @Test
    void theInputFragmentCarriesTheDefaultTextAndUnitsItWasGiven() {
        // when
        String html = render("input('schedule', '', 5, #{store.marketplaces.schedule.summary.default(10)}, 'minutes,hours')");

        // then
        assertThat(html).contains("data-default-text=\"Default — every 10 min\"");
        assertThat(html).contains("data-units=\"minutes,hours\"");
        assertThat(html).doesNotContain("once a day");
    }

    @Test
    void theScriptReadsTheDefaultTextAndUnitsOffTheFieldInsteadOfHardCodingThem() {
        // when
        String html = render("script");

        // then
        assertThat(html).contains("field.getAttribute('data-default-text')");
        assertThat(html).contains("field.getAttribute('data-units')");
        assertThat(html).contains("if (units.indexOf(option.value) < 0) { option.remove(); }");
        assertThat(html).contains("function offers(unit, value)");
    }

    /**
     * The mode toggle used to be a Bulma tabs bar built from a plain list, which the page's `.content` typography indented;
     * it is now a group of pressed/unpressed buttons, and the hours are toggle chips the script marks with aria-pressed.
     */
    @Test
    void theModesAreToggleButtonsAndTheHoursToggleChips() {
        // when
        String input = render("input('feedSchedule', '0 5 * * ? *', 5, 'Default', 'minutes,hours,days')");
        String script = render("script");

        // then
        assertThat(input).contains("class=\"cl-segmented schedule-modes\" role=\"group\"")
                .contains("<button type=\"button\" class=\"cl-segment\" data-mode=\"at\" aria-pressed=\"false\"")
                .doesNotContain("<ul").doesNotContain("tabs is-toggle");
        assertThat(script).contains("button.className = 'cl-chip';")
                .contains("querySelectorAll('[aria-pressed=\"true\"]')")
                .doesNotContain("is-link");
    }

    @Test
    void theStylesLiveInTheSharedStylesheetAndTheFragmentStaysForThePagesIncludingIt() {
        // when
        String html = render("style");

        // then
        assertThat(html).contains("<style></style>");
    }

    @Test
    void theScriptFragmentInlinesTheSameSummaryTextsTheTableCellUses() {
        // when
        String html = render("script");

        // then -- javascript inlining escapes non-ASCII, so the em dash arrives as —
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Default \\u2014 once a day");
        assertThat(html).contains("Every {0} min");
        assertThat(html).contains("On weekdays at {0}");
        // a stored expression the builder cannot show is named, with the expression itself
        assertThat(html).contains("The stored schedule ({0}) does not fit the builder.");
        assertThat(html).contains("window.scheduleField");
    }

    /** Without JavaScript the stored schedule is still named, and the mode buttons that could not work are hidden. */
    @Test
    void theSummaryOfTheStoredScheduleIsRenderedByTheServerAndTheModesWaitForTheScript() {
        // when
        String every = render("input('feedSchedule', '0/30 * * * ? *', 5, 'Default text', 'minutes,hours,days')");
        String unset = render("input('feedSchedule', null, 5, 'Default text', 'minutes,hours,days')");

        // then
        assertThat(every).containsPattern("<p class=\"cl-help schedule-summary\" aria-live=\"polite\">Every 30 min</p>");
        assertThat(unset).contains(">Default text</p>");
        assertThat(every).contains("class=\"cl-segmented schedule-modes\" role=\"group\"").contains("hidden");
        assertThat(render("script")).contains("modes.hidden = false");
    }
}
