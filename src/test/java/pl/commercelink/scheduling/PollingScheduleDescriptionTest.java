package pl.commercelink.scheduling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PollingScheduleDescriptionTest {

    @Test
    void blankScheduleIsTheNightlyDefault() {
        // when / then
        assertThat(PollingScheduleDescription.of(null).code()).isEqualTo(PollingScheduleDescription.DEFAULT);
        assertThat(PollingScheduleDescription.of("  ").code()).isEqualTo(PollingScheduleDescription.DEFAULT);
    }

    @Test
    void describesAnEveryMinutesSchedule() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("0/30 * * * ? *");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.EVERY_MINUTES);
        assertThat(description.args()).isEqualTo(List.of(30));
    }

    @Test
    void describesTheHourlyScheduleWrittenWithAStar() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("0 * * * ? *");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.EVERY_HOURS);
        assertThat(description.args()).isEqualTo(List.of(1));
    }

    @Test
    void describesAnEveryHoursSchedule() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("0 0/6 * * ? *");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.EVERY_HOURS);
        assertThat(description.args()).isEqualTo(List.of(6));
    }

    @Test
    void describesAnEveryDaysSchedule() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("0 0 1/3 * ? *");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.EVERY_DAYS);
        assertThat(description.args()).isEqualTo(List.of(3));
    }

    @Test
    void listsTheTimesOfADailySchedule() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("30 5,17 * * ? *");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.AT);
        assertThat(description.args()).isEqualTo(List.of("05:30, 17:30"));
    }

    @Test
    void namesTheDayOfWeekOfARestrictedSchedule() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("0 6 ? * MON-FRI *");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.AT + ".MON-FRI");
        assertThat(description.args()).isEqualTo(List.of("06:00"));
    }

    @Test
    void normalizesBeforeDescribing() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("  0   6 ? * mon *  ");

        // then
        assertThat(description.code()).isEqualTo(PollingScheduleDescription.AT + ".MON");
        assertThat(description.args()).isEqualTo(List.of("06:00"));
    }

    @Test
    void ordersTheCrossProductChronologically() {
        // when
        PollingScheduleDescription description = PollingScheduleDescription.of("45,15 17,5 * * ? *");

        // then
        assertThat(description.args()).isEqualTo(List.of("05:15, 05:45, 17:15, 17:45"));
    }

    @Test
    void fallsBackToCustomRatherThanListingTooManyTimes() {
        // given -- eight runs a day, past the point where a table cell stays readable
        String everyThreeHoursSpeltOut = "0 0,3,6,9,12,15,18,21 * * ? *";

        // when / then
        assertThat(PollingScheduleDescription.of(everyThreeHoursSpeltOut).code())
                .isEqualTo(PollingScheduleDescription.CUSTOM);
    }

    @Test
    void fallsBackToCustomForExpressionsTheBuilderCannotWrite() {
        // given -- ranges, steps inside a range and month restrictions all belong to the advanced
        // field; paraphrasing them wrongly would be worse than saying nothing
        List<String> beyondTheBuilder = List.of(
                "0 9-17 * * ? *",
                "0/15 9-17 * * ? *",
                "0 6 ? 3 MON *",
                "0 6 15 * ? *",
                "not a cron");

        // when / then
        beyondTheBuilder.forEach(expression -> assertThat(PollingScheduleDescription.of(expression).code())
                .as(expression)
                .isEqualTo(PollingScheduleDescription.CUSTOM));
    }

    @Test
    void exposesArgumentsInTheShapeThymeleafNeeds() {
        // when
        Object[] args = PollingScheduleDescription.of("0/30 * * * ? *").messageArgs();

        // then
        assertThat(args).containsExactly(30);
    }
}
