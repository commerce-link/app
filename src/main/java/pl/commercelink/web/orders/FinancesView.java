package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderFinancials;
import pl.commercelink.orders.OrderItem;

import java.util.List;

/** Totals of the order with its cost and profit; every role that opens the order sees them. */
public record FinancesView(String total, String paid, String unpaid, boolean unpaidDue, Costs costs) {

    public record Costs(String itemsCost, String servicesCost, String feesCost, String profit, String profitNet) {
    }

    public static FinancesView of(Order order, List<OrderItem> items) {
        OrderFinancials financials = new OrderFinancials(order, items);
        double unpaid = order.getUnpaidAmount();
        Costs costs = new Costs(Money.format(financials.getTotalItemsCostGross()),
                Money.format(financials.getTotalServicesCostGross()), Money.format(financials.getTotalProcessingFeesCostGross()),
                Money.format(financials.getTotalProfit()), Money.format(financials.getTotalProfitNet()));
        return new FinancesView(Money.format(order.getTotalPrice()), Money.format(order.getPaidAmount()), Money.format(unpaid),
                unpaid > 0.005, costs);
    }
}
