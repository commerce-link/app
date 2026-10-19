package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import pl.commercelink.financials.FinancialReport;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelComparisonTest {

    @Test
    void comparesTheChannelsMeasureByMeasure() {
        // when
        ChannelComparison comparison = ChannelComparison.of(report(141, 254_162.49, 215_438.97, 38_723.66),
                report(324, 554_680.10, 467_641.91, 87_038.44));

        // then
        assertThat(comparison.totals().salesNet()).isEqualTo("808 843");
        assertThat(comparison.totals().margin()).isEqualTo("15,5%");
        assertThat(comparison.totals().orders()).isEqualTo(465);
        assertThat(comparison.rows()).extracting(ChannelComparison.Row::labelKey).containsExactly(
                "reports.channels.orders", "reports.channels.sales", "reports.channels.profit");
        ChannelComparison.Row orders = comparison.rows().get(0);
        assertThat(orders.own()).isEqualTo("141");
        assertThat(orders.share().ownText()).isEqualTo("30,3%");
        assertThat(orders.share().ownLabelled()).isTrue();
        assertThat(comparison.missing()).isNull();
        assertThat(comparison.rows()).extracting(ChannelComparison.Row::money).containsExactly(false, true, true);
    }

    @Test
    void figuresThatDoNotAddUpAreAProfileOfEachChannel() {
        // when
        ChannelComparison comparison = ChannelComparison.of(report(141, 254_162.49, 215_438.97, 38_723.66),
                report(324, 554_680.10, 467_641.91, 87_038.44));

        // then
        assertThat(comparison.profiles()).extracting(ChannelComparison.Profile::channel)
                .containsExactly(ChannelComparison.Channel.OWN, ChannelComparison.Channel.MARKETPLACE);
        ChannelComparison.Profile own = comparison.profiles().get(0);
        assertThat(own.costNet()).isEqualTo("215 439");
        assertThat(own.margin()).isEqualTo("15,2%");
        assertThat(own.averageOrder()).isEqualTo("1 803");
        ChannelComparison.Profile marketplace = comparison.profiles().get(1);
        assertThat(marketplace.costNet()).isEqualTo("467 642");
        assertThat(marketplace.margin()).isEqualTo("15,7%");
        assertThat(marketplace.averageOrder()).isEqualTo("1 712");
    }

    @Test
    void aNegativeProfitLosesItsShareBar() {
        // when
        ChannelComparison comparison = ChannelComparison.of(report(10, 1_000, 1_200, -200), report(10, 1_000, 800, 200));

        // then
        ChannelComparison.Row profit = comparison.rows().get(2);
        assertThat(profit.share()).isNull();
        assertThat(profit.own()).isEqualTo("−200");
    }

    @Test
    void aChannelWithoutOrdersIsNamedAndShownAsADash() {
        // when
        ChannelComparison comparison = ChannelComparison.of(report(5, 500, 400, 100), report(0, 0, 0, 0));

        // then
        assertThat(comparison.missing()).isEqualTo(ChannelComparison.Channel.MARKETPLACE);
        ChannelComparison.Profile marketplace = comparison.profiles().get(1);
        assertThat(marketplace.costNet()).isEqualTo("0");
        assertThat(marketplace.margin()).isNull();
        assertThat(marketplace.averageOrder()).isNull();
        assertThat(comparison.rows().get(0).share().own()).isEqualTo(100);
    }

    @Test
    void aTinyShareKeepsItsPercentOutOfTheSegment() {
        // when
        ChannelComparison comparison = ChannelComparison.of(report(3, 300, 200, 100), report(97, 9_700, 8_000, 1_700));

        // then
        assertThat(comparison.rows().get(0).share().ownLabelled()).isFalse();
        assertThat(comparison.rows().get(0).share().marketplaceLabelled()).isTrue();
    }

    @Test
    void anEmptyPeriodIsEmpty() {
        // then
        assertThat(ChannelComparison.of(report(0, 0, 0, 0), report(0, 0, 0, 0)).empty()).isTrue();
    }

    private static FinancialReport report(int orders, double price, double cost, double profit) {
        return new FinancialReport(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), orders, price, cost, profit,
                Map.of(), 0, 0, 0);
    }
}
