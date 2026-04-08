package pl.commercelink.orders;

import pl.commercelink.invoicing.api.Price;
import pl.commercelink.taxonomy.ProductGroup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class OrderFinancials {

    private double totalItemsPrice;
    private double totalItemsCost;
    private double totalServicesPrice;
    private double totalServicesCost;
    private double totalProcessingFeesCost;
    private double totalPrice;
    private double totalPriceNet;
    private double totalCost;
    private double totalCostNet;
    private double totalProfit;
    private double totalProfitNet;
    private double paidAmount;
    private double unpaidAmount;

    public OrderFinancials(Order order, List<OrderItem> orderItems) {
        this.totalItemsPrice = orderItems.stream().filter(i -> !i.hasGroup(ProductGroup.Services)).mapToDouble(OrderItem::getTotalPrice).sum();
        this.totalItemsCost = orderItems.stream().filter(i -> !i.hasGroup(ProductGroup.Services)).mapToDouble(OrderItem::getTotalCost).sum();
        this.totalServicesPrice = orderItems.stream().filter(i -> i.hasGroup(ProductGroup.Services)).mapToDouble(OrderItem::getTotalPrice).sum();
        this.totalServicesCost = orderItems.stream().filter(i -> i.hasGroup(ProductGroup.Services)).mapToDouble(OrderItem::getTotalCost).sum();
        this.totalProcessingFeesCost = order.getPayments().stream().mapToDouble(Payment::getProcessingFee).sum();

        this.totalPrice = totalItemsPrice + totalServicesPrice;
        this.totalPriceNet = Price.fromGross(totalPrice).netValue();

        this.totalCostNet = totalItemsCost + totalServicesCost + totalProcessingFeesCost;
        this.totalCost = Price.fromNet(totalCostNet).grossValue();

        this.totalProfit = totalPrice - totalCost;
        this.totalProfitNet = Price.fromGross(totalProfit).netValue();

        this.paidAmount = order.getPayments().stream().mapToDouble(Payment::getAmount).sum();
        this.unpaidAmount = totalPrice - paidAmount;
    }

    public double getTotalItemsPrice() {
        return totalItemsPrice;
    }

    public void setTotalItemsPrice(double totalItemsPrice) {
        this.totalItemsPrice = totalItemsPrice;
    }

    public double getTotalItemsCost() {
        return totalItemsCost;
    }

    public double getTotalItemsCostGross() {
        return Price.fromNet(totalItemsCost).grossValue();
    }

    public void setTotalItemsCost(double totalItemsCost) {
        this.totalItemsCost = totalItemsCost;
    }

    public double getTotalServicesPrice() {
        return totalServicesPrice;
    }

    public void setTotalServicesPrice(double totalServicesPrice) {
        this.totalServicesPrice = totalServicesPrice;
    }

    public double getTotalServicesCost() {
        return totalServicesCost;
    }

    public double getTotalServicesCostGross() {
        return Price.fromNet(totalServicesCost).grossValue();
    }

    public void setTotalServicesCost(double totalServicesCost) {
        this.totalServicesCost = totalServicesCost;
    }

    public double getTotalProcessingFeesCost() {
        return totalProcessingFeesCost;
    }

    public double getTotalProcessingFeesCostGross() {
        return Price.fromNet(totalProcessingFeesCost).grossValue();
    }

    public void setTotalProcessingFeesCost(double totalProcessingFeesCost) {
        this.totalProcessingFeesCost = totalProcessingFeesCost;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public double getTotalPriceNet() {
        return totalPriceNet;
    }

    public void setTotalPriceNet(double totalPriceNet) {
        this.totalPriceNet = totalPriceNet;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public double getTotalCostNet() {
        return totalCostNet;
    }

    public void setTotalCostNet(double totalCostNet) {
        this.totalCostNet = totalCostNet;
    }

    public double getTotalProfit() {
        return BigDecimal.valueOf(totalProfit).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public void setTotalProfit(double totalProfit) {
        this.totalProfit = totalProfit;
    }

    public double getTotalProfitNet() {
        return BigDecimal.valueOf(totalProfitNet).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public void setTotalProfitNet(double totalProfitNet) {
        this.totalProfitNet = totalProfitNet;
    }

    public double getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(double paidAmount) {
        this.paidAmount = paidAmount;
    }

    public double getUnpaidAmount() {
        return unpaidAmount;
    }

    public void setUnpaidAmount(double unpaidAmount) {
        this.unpaidAmount = unpaidAmount;
    }
}
