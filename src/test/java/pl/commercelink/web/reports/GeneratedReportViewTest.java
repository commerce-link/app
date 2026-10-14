package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import pl.commercelink.reports.GeneratedReports;
import pl.commercelink.reports.ReportType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedReportViewTest {

    @Test
    void theViewShowsTheTitleTheRangeTheWarsawTimeAndTheDownloadOfTheRange() {
        // given
        Locale pl = Locale.forLanguageTag("pl");
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("reports.generated.type.STOCK_LEDGER_EXPORT", pl, "Obroty");
        var entry = new GeneratedReports.Entry(ReportType.STOCK_LEDGER_EXPORT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                Instant.parse("2026-10-14T08:05:00Z"));

        // when
        GeneratedReportView view = GeneratedReportView.of(entry, "/generated", messages, pl);

        // then
        assertThat(view.title()).isEqualTo("Obroty");
        assertThat(view.periodText()).isEqualTo("1–30.09.2026");
        assertThat(view.generatedAt()).isEqualTo("14.10.2026 10:05");
        assertThat(view.downloadHref()).isEqualTo("/generated?dateFrom=2026-09-01&dateTo=2026-09-30");
    }
}
