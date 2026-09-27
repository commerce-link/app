package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderFinancials;
import pl.commercelink.orders.OrderItem;

import java.util.List;

/**
 * Totals of the order; cost and profit only for an admin, and then only in the model (never rendered for a
 * user, not merely hidden by CSS).
 */
public record FinancesView(String itemsValue, String servicesValue, String total, String paid, String unpaid,
                           boolean unpaidDue, AdminCosts costs) {

    public record AdminCosts(String itemsCost, String servicesCost, String feesCost, String profit, String profitNet) {
    }

    public static FinancesView of(Order order, List<OrderItem> items, boolean admin) {
        OrderFinancials financials = new OrderFinancials(order, items);
        double unpaid = order.getUnpaidAmount();
        AdminCosts costs = admin ? new AdminCosts(Money.format(financials.getTotalItemsCostGross()),
                Money.format(financials.getTotalServicesCostGross()), Money.format(financials.getTotalProcessingFeesCostGross()),
                Money.format(financials.getTotalProfit()), Money.format(financials.getTotalProfitNet())) : null;
        return new FinancesView(Money.format(financials.getTotalItemsPrice()), Money.format(financials.getTotalServicesPrice()),
                Money.format(order.getTotalPrice()), Money.format(order.getPaidAmount()), Money.format(unpaid),
                unpaid > 0.005, costs);
    }
}
