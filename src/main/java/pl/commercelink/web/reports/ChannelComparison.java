package pl.commercelink.web.reports;

import pl.commercelink.financials.FinancialReport;

import java.util.List;

/** The "store and marketplace" card: three totals and the two channels side by side, one measure per row. */
public record ChannelComparison(Totals totals, List<Row> rows, Channel missing) {

    /** Below this share the percent no longer fits inside its segment; it stays in the bar's title and label. */
    private static final double LABELLED_SHARE = 12;

    public enum Channel { OWN, MARKETPLACE }

    public record Totals(String salesNet, String profitNet, String margin, int orders, String averageOrder) {
    }

    public record Row(String labelKey, boolean money, String own, String marketplace, Share share) {
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
        boolean ownSold = own.getTotalNumberOfOrders() > 0;
        boolean marketplaceSold = marketplace.getTotalNumberOfOrders() > 0;
        List<Row> rows = List.of(
                new Row("reports.channels.orders", false, String.valueOf(own.getTotalNumberOfOrders()),
                        String.valueOf(marketplace.getTotalNumberOfOrders()),
                        Share.of(own.getTotalNumberOfOrders(), marketplace.getTotalNumberOfOrders())),
                new Row("reports.channels.sales", true, ReportNumbers.wholeAmount(own.getTotalPrice()),
                        ReportNumbers.wholeAmount(marketplace.getTotalPrice()), Share.of(own.getTotalPrice(), marketplace.getTotalPrice())),
                new Row("reports.channels.profit", true, ReportNumbers.wholeAmount(own.getTotalProfit()),
                        ReportNumbers.wholeAmount(marketplace.getTotalProfit()), Share.of(own.getTotalProfit(), marketplace.getTotalProfit())),
                new Row("reports.channels.cost", true, ReportNumbers.wholeAmount(own.getTotalCost()),
                        ReportNumbers.wholeAmount(marketplace.getTotalCost()), null),
                new Row("reports.channels.margin", false, ownSold ? margin(own.getTotalProfit(), own.getTotalPrice()) : null,
                        marketplaceSold ? margin(marketplace.getTotalProfit(), marketplace.getTotalPrice()) : null, null),
                new Row("reports.channels.averageOrder", true,
                        ownSold ? ReportNumbers.wholeAmount(own.getTotalPrice() / own.getTotalNumberOfOrders()) : null,
                        marketplaceSold ? ReportNumbers.wholeAmount(marketplace.getTotalPrice() / marketplace.getTotalNumberOfOrders()) : null,
                        null));
        return new ChannelComparison(totals, rows, missing);
    }

    public boolean empty() {
        return totals.orders() == 0;
    }

    private static String margin(double profit, double sales) {
        return sales > 0 ? ReportNumbers.percent(profit / sales * 100) : null;
    }
}
