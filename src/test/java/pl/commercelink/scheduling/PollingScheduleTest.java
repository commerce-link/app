package pl.commercelink.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollingScheduleTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0/30 * * * ? *",
            "0 * * * ? *",
            "0/30 9-17 * * ? *",
            "0 6,14 * * ? *",
            "15 5 * * ? *",
            "0 8 ? * MON-FRI *",
            "0 8 ? * 2-6 2026",
            "0 0 L * ? *",
            "0 12 ? * 6L *",
            "0 12 ? * 2#1 *",
            "0 7 1,15 JAN-JUN ? *",
            "0,30 9 * * ? *",
            "5/20 * * * ? *",
            "0,58 9,14 * * ? *",
            "0,58 23 * * ? *"
    })
    void acceptsValidEventBridgeCronExpressions(String expression) {
        // when
        PollingSchedule schedule = PollingSchedule.parse(expression, 5);

        // then
        assertThat(schedule.awsExpression()).isEqualTo("cron(" + expression.toUpperCase() + ")");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "0 5 * * ?",
            "0 5 * * * * *",
            "0 5 * * * *",
            "0 5 ? * ? *",
            "60 5 * * ? *",
            "0 24 * * ? *",
            "a 5 * * ? *",
            "0/0 * * * ? *",
            "30-10 * * * ? *",
            "0 5 32 * ? *",
            "0 5 * 13 ? *",
            "0 5 ? * 8 *",
            "0 5 * * ? 1969"
    })
    void rejectsMalformedExpressions(String expression) {
        // when / then
        assertThatThrownBy(() -> PollingSchedule.parse(expression, 5))
                .isInstanceOf(InvalidScheduleException.class)
                .extracting(e -> ((InvalidScheduleException) e).getReason())
                .isEqualTo(InvalidScheduleException.Reason.SYNTAX);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "* * * * ? *",
            "*/1 * * * ? *",
            "0/2 * * * ? *",
            "0,3 9 * * ? *",
            "0,58 * * * ? *",
            "0-59/4 * * * ? *",
            "0-10 9 * * ? *",
            "0,58 9,10 * * ? *",
            "0,58 23,0 * * ? *",
            "0,58 9-17 * * ? *",
            "0,58 22-23 * * ? *"
    })
    void rejectsSchedulesRunningMoreOftenThanTheFloor(String expression) {
        // when / then
        assertThatThrownBy(() -> PollingSchedule.parse(expression, 5))
                .isInstanceOf(InvalidScheduleException.class)
                .satisfies(e -> {
                    InvalidScheduleException invalid = (InvalidScheduleException) e;
                    assertThat(invalid.getReason()).isEqualTo(InvalidScheduleException.Reason.TOO_FREQUENT);
                    assertThat(invalid.getMinIntervalMinutes()).isEqualTo(5);
                });
    }

    @Test
    void ignoresHourWrapAroundWhenOnlyOneHourFires() {
        // when
        PollingSchedule schedule = PollingSchedule.parse("0,58 9 * * ? *", 5);

        // then
        assertThat(schedule.expression()).isEqualTo("0,58 9 * * ? *");
    }

    @Test
    void countsHourWrapAroundOnlyBetweenAdjacentHours() {
        // when
        PollingSchedule apart = PollingSchedule.parse("0,58 9,14 * * ? *", 5);

        // then
        assertThat(apart.expression()).isEqualTo("0,58 9,14 * * ? *");
        assertThatThrownBy(() -> PollingSchedule.parse("0,58 9,10 * * ? *", 5))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void storedOrRandomNightlyKeepsAStoredCronAndFallsBackWhenBlank() {
        // when / then
        assertThat(PollingSchedule.storedOrRandomNightly(" 0 5 * * ? * ").awsExpression()).isEqualTo("cron(0 5 * * ? *)");
        assertThat(PollingSchedule.storedOrRandomNightly("  ").awsExpression()).matches("cron\\(\\d{1,2} (23|0|1|2|3|4) \\* \\* \\? \\*\\)");
        assertThat(PollingSchedule.storedOrRandomNightly(null).awsExpression()).matches("cron\\(\\d{1,2} (23|0|1|2|3|4) \\* \\* \\? \\*\\)");
    }

    @Test
    void floorOfOneMinuteAcceptsEveryMinute() {
        // when
        PollingSchedule schedule = PollingSchedule.parse("* * * * ? *", 1);

        // then
        assertThat(schedule.awsExpression()).isEqualTo("cron(* * * * ? *)");
    }

    @Test
    void normalizesWhitespace() {
        // when
        PollingSchedule schedule = PollingSchedule.parse("  0/30   9-17 *  * ?   * ", 5);

        // then
        assertThat(schedule.expression()).isEqualTo("0/30 9-17 * * ? *");
    }

    @Test
    void normalizeOrNullCollapsesWhitespaceUpperCasesAndTurnsBlankIntoNull() {
        // when / then
        assertThat(PollingSchedule.normalizeOrNull("  0/30  * * * ?  * ")).isEqualTo("0/30 * * * ? *");
        assertThat(PollingSchedule.normalizeOrNull("0 5 ? * mon-fri *")).isEqualTo("0 5 ? * MON-FRI *");
        assertThat(PollingSchedule.normalizeOrNull("   ")).isNull();
        assertThat(PollingSchedule.normalizeOrNull(null)).isNull();
    }

    @Test
    void randomNightlyFallsBetweenElevenPmAndFiveAm() {
        for (int i = 0; i < 200; i++) {
            // when
            PollingSchedule schedule = PollingSchedule.randomNightly();

            // then
            String[] fields = schedule.expression().split(" ");
            assertThat(fields).hasSize(6);
            assertThat(Integer.parseInt(fields[0])).isBetween(0, 59);
            assertThat(Integer.parseInt(fields[1])).isIn(23, 0, 1, 2, 3, 4);
            assertThat(schedule.awsExpression()).startsWith("cron(").endsWith("* * ? *)");
        }
    }
}
