package pl.commercelink.financials;

import java.time.LocalDate;

/** Net sales, net profit and order count of one day, apart for the store's own channels and the marketplaces. */
public record DailySales(LocalDate day, double ownPriceNet, double ownProfitNet, int ownOrders,
                         double marketplacePriceNet, double marketplaceProfitNet, int marketplaceOrders) {

    static DailySales empty(LocalDate day) {
        return new DailySales(day, 0, 0, 0, 0, 0, 0);
    }

    DailySales plus(boolean marketplace, double priceNet, double profitNet) {
        return marketplace
                ? new DailySales(day, ownPriceNet, ownProfitNet, ownOrders,
                marketplacePriceNet + priceNet, marketplaceProfitNet + profitNet, marketplaceOrders + 1)
                : new DailySales(day, ownPriceNet + priceNet, ownProfitNet + profitNet, ownOrders + 1,
                marketplacePriceNet, marketplaceProfitNet, marketplaceOrders);
    }
}
