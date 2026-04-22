package pl.commercelink.orders;

import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

public class DeliveryDays {

    private final int estimatedAssemblyDays;
    private final int minRealizationDays;
    private final int maxRealizationDays;

    public DeliveryDays(int estimatedAssemblyDays, int minRealizationDays, int maxRealizationDays) {
        this.estimatedAssemblyDays = estimatedAssemblyDays;
        this.minRealizationDays = minRealizationDays;
        this.maxRealizationDays = maxRealizationDays;
    }

    public static DeliveryDays calculate(Store store, Basket basket) {
        FulfilmentConfiguration fulfilmentConfiguration = store.getFulfilmentConfiguration();

        int estimatedAssemblyDate = fulfilmentConfiguration.getOrderAssemblyDays();
        for (BasketItem item : basket.getBasketItemsForProducts()) {
            estimatedAssemblyDate = Math.max(estimatedAssemblyDate, item.getEstimatedDeliveryDays());
        }

        int estimatedRealizationDays = fulfilmentConfiguration.getOrderRealizationDays();
        for (BasketItem item : basket.getBasketItemsForServices()) {
            estimatedRealizationDays = Math.max(estimatedRealizationDays, item.getEstimatedDeliveryDays());
        }

        return new DeliveryDays(
                estimatedAssemblyDate,
                fulfilmentConfiguration.getOrderRealizationDays(),
                estimatedRealizationDays
        );
    }

    public int getEstimatedAssemblyDays() {
        return estimatedAssemblyDays;
    }

    public int getMinRealizationDays() {
        return minRealizationDays;
    }

    public int getMaxRealizationDays() {
        return maxRealizationDays;
    }

    public int getMinEstimatedDeliveryDays() {
        return estimatedAssemblyDays + minRealizationDays;
    }

    public int getMaxEstimatedDeliveryDays() {
        if (minRealizationDays == maxRealizationDays) {
            return getMinEstimatedDeliveryDays() + 1;
        }
        return getMinEstimatedDeliveryDays() + maxRealizationDays;
    }
}
