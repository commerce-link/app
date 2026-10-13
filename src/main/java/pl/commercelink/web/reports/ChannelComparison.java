package pl.commercelink.web.reports;

import pl.commercelink.financials.FinancialReport;

import java.util.List;

/**
 * The "store and marketplace" card: three totals, the measures that add up (orders, sales, profit) side by side with
 * each channel's share, and a profile of each channel with the figures that do not add up into a share.
 */
public record ChannelComparison(Totals totals, List<Row> rows, List<Profile> profiles, Channel missing) {

    /** Below this share the percent no longer fits inside its segment; it stays in the bar's title and label. */
    private static final double LABELLED_SHARE = 12;

    public enum Channel { OWN, MARKETPLACE }

    public record Totals(String salesNet, String profitNet, String margin, int orders, String averageOrder) {

        public String ordersText() {
            return ReportNumbers.count(orders);
        }
    }

    public record Row(String labelKey, boolean money, String own, String marketplace, Share share) {
    }

    /** Net cost, margin and average order of one channel; margin and average order are null without orders. */
    public record Profile(Channel channel, String costNet, String margin, String averageOrder) {
    }

    public record Share(double own, double marketplace, String ownText, String marketplaceText,
                        boolean ownLabelled, boolean marketplaceLabelled) {

        static Share of(double own, double marketplace) {
            if (own < 0 || marketplace < 0 || own + marketplace <= 0) {
                return null;
            }
            double ownPercent = own / (own + marketplace) * 100;
            double marketplacePercent = 100 - ownPercent;
            return new Share(ownPercent, marketplacePercent, ReportNumbers.percent(ownPercent),
                    ReportNumbers.percent(marketplacePercent), ownPercent >= LABELLED_SHARE, marketplacePercent >= LABELLED_SHARE);
        }
    }

    public static ChannelComparison of(FinancialReport own, FinancialReport marketplace) {
        int orders = own.getTotalNumberOfOrders() + marketplace.getTotalNumberOfOrders();
        double sales = own.getTotalPrice() + marketplace.getTotalPrice();
        double profit = own.getTotalProfit() + marketplace.getTotalProfit();
        Totals totals = new Totals(ReportNumbers.wholeAmount(sales), ReportNumbers.wholeAmount(profit),
                margin(profit, sales), orders, orders == 0 ? null : ReportNumbers.wholeAmount(sales / orders));
        Channel missing = orders == 0 ? null
                : own.getTotalNumberOfOrders() == 0 ? Channel.OWN
                : marketplace.getTotalNumberOfOrders() == 0 ? Channel.MARKETPLACE : null;
        List<Row> rows = List.of(
                new Row("reports.channels.orders", false, ReportNumbers.count(own.getTotalNumberOfOrders()),
                        ReportNumbers.count(marketplace.getTotalNumberOfOrders()),
                        Share.of(own.getTotalNumberOfOrders(), marketplace.getTotalNumberOfOrders())),
                new Row("reports.channels.sales", true, ReportNumbers.wholeAmount(own.getTotalPrice()),
                        ReportNumbers.wholeAmount(marketplace.getTotalPrice()), Share.of(own.getTotalPrice(), marketplace.getTotalPrice())),
                new Row("reports.channels.profit", true, ReportNumbers.wholeAmount(own.getTotalProfit()),
                        ReportNumbers.wholeAmount(marketplace.getTotalProfit()), Share.of(own.getTotalProfit(), marketplace.getTotalProfit())));
        List<Profile> profiles = List.of(profile(Channel.OWN, own), profile(Channel.MARKETPLACE, marketplace));
        return new ChannelComparison(totals, rows, profiles, missing);
    }

    private static Profile profile(Channel channel, FinancialReport report) {
        int orders = report.getTotalNumberOfOrders();
        return new Profile(channel, ReportNumbers.wholeAmount(report.getTotalCost()),
                orders > 0 ? margin(report.getTotalProfit(), report.getTotalPrice()) : null,
                orders > 0 ? ReportNumbers.wholeAmount(report.getTotalPrice() / orders) : null);
    }

    public boolean empty() {
        return totals.orders() == 0;
    }

    private static String margin(double profit, double sales) {
        return sales > 0 ? ReportNumbers.percent(profit / sales * 100) : null;
    }
}
