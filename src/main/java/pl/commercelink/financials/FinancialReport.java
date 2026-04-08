package pl.commercelink.financials;

import java.time.LocalDate;
import java.util.Map;

public class FinancialReport {
    private static final double TAX = 0.81;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final int totalNumberOfOrders;
    private final double totalPrice;
    private final double totalCost;
    private final double totalProfit;
    private final Map<String, Integer> salesVolumeByProvider;
    private final int totalDeliveries;
    private final double totalShippingCost;
    private final double totalPaymentCost;

    public FinancialReport(LocalDate dateFrom, LocalDate dateTo, int totalNumberOfOrders, double totalPrice, double totalCost, double totalProfit, Map<String, Integer> salesVolumeByProvider, int totalDeliveries, double totalShippingCost, double totalPaymentCost) {
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.totalNumberOfOrders = totalNumberOfOrders;
        this.totalPrice = totalPrice;
        this.totalCost = totalCost;
        this.totalProfit = totalProfit;
        this.salesVolumeByProvider = salesVolumeByProvider;
        this.totalDeliveries = totalDeliveries;
        this.totalShippingCost = totalShippingCost;
        this.totalPaymentCost = totalPaymentCost;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public int getTotalNumberOfOrders() {
        return totalNumberOfOrders;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public double getTotalProfit() {
        return totalProfit;
    }

    public Map<String, Integer> getSalesVolumeByProvider() {
        return salesVolumeByProvider;
    }

    public int getTotalDeliveries() {
        return totalDeliveries;
    }

    public double getTotalShippingCost() {
        return totalShippingCost;
    }

    public double getTotalPaymentCost() {
        return totalPaymentCost;
    }
}
