package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportNumbersTest {

    @Test
    void formatsAmountsPercentsAndAxisValues() {
        // then
        assertThat(ReportNumbers.wholeAmount(254_162.49)).isEqualTo("254 162");
        assertThat(ReportNumbers.wholeAmount(-38.6)).isEqualTo("−39");
        assertThat(ReportNumbers.wholeAmount(0)).isEqualTo("0");
        assertThat(ReportNumbers.percent(30.345)).isEqualTo("30,3%");
        assertThat(ReportNumbers.axis(15_000)).isEqualTo(new ReportNumbers.Axis("15", "reports.axis.thousands"));
        assertThat(ReportNumbers.axis(1_000_000)).isEqualTo(new ReportNumbers.Axis("1", "reports.axis.millions"));
        assertThat(ReportNumbers.axis(2_500_000)).isEqualTo(new ReportNumbers.Axis("2,5", "reports.axis.millions"));
        assertThat(ReportNumbers.axis(15)).isEqualTo(new ReportNumbers.Axis("15", null));
    }
}
