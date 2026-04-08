package pl.commercelink.inventory.deliveries;

import java.time.LocalDate;

public class DeliveryFilter {
    private final String deliveryId;
    private final String externalDeliveryId;
    private final String provider;
    private final LocalDate orderedAtStart;
    private final LocalDate orderedAtEnd;
    private final boolean waitingForCollection;
    private final boolean withoutInvoice;
    private final boolean withoutSync;

    public DeliveryFilter(String deliveryId, String externalDeliveryId, String provider, LocalDate orderedAtStart, LocalDate orderedAtEnd, boolean waitingForCollection, boolean withoutInvoice, boolean withoutSync) {
        this.deliveryId = deliveryId;
        this.externalDeliveryId = externalDeliveryId;
        this.provider = provider;
        this.orderedAtStart = orderedAtStart;
        this.orderedAtEnd = orderedAtEnd;
        this.waitingForCollection = waitingForCollection;
        this.withoutInvoice = withoutInvoice;
        this.withoutSync = withoutSync;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getExternalDeliveryId() {
        return externalDeliveryId;
    }

    public String getProvider() {
        return provider;
    }

    public LocalDate getOrderedAtStart() {
        return orderedAtStart;
    }

    public LocalDate getOrderedAtEnd() {
        return orderedAtEnd;
    }

    public boolean isWaitingForCollection() {
        return waitingForCollection;
    }

    public boolean isWithoutInvoice() {
        return withoutInvoice;
    }

    public boolean isWithoutSync() {
        return withoutSync;
    }
}
