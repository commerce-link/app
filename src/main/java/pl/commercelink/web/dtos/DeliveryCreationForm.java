package pl.commercelink.web.dtos;

import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

public class DeliveryCreationForm {

    private String storeId;
    private String provider;

    private String externalDeliveryId;
    private String sourceCurrency = "PLN";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate estimatedDeliveryAt;
    private double shippingCost;
    private double paymentCost;
    private double tax = DEFAULT_VAT_RATE;
    private int paymentTerms;
    private boolean removeUnselected;
    private String deliveryAddressId;

    private List<DeliveryItem> items = new ArrayList<>();
    private List<SuggestedDeliveryItem> suggestedItems = new ArrayList<>();

    public DeliveryCreationForm() {}

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDeliveryAddressId() {
        return deliveryAddressId;
    }

    public void setDeliveryAddressId(String deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    public String getExternalDeliveryId() {
        return externalDeliveryId;
    }

    public void setExternalDeliveryId(String externalDeliveryId) {
        this.externalDeliveryId = externalDeliveryId;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    public LocalDate getEstimatedDeliveryAt() {
        return estimatedDeliveryAt;
    }

    public void setEstimatedDeliveryAt(LocalDate estimatedDeliveryAt) {
        this.estimatedDeliveryAt = estimatedDeliveryAt;
    }

    public double getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(double shippingCost) {
        this.shippingCost = shippingCost;
    }

    public double getPaymentCost() {
        return paymentCost;
    }

    public void setPaymentCost(double paymentCost) {
        this.paymentCost = paymentCost;
    }

    public double getTax() {
        return tax;
    }

    public void setTax(double tax) {
        this.tax = tax;
    }

    public int getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(int paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public boolean isRemoveUnselected() {
        return removeUnselected;
    }

    public void setRemoveUnselected(boolean removeUnselected) {
        this.removeUnselected = removeUnselected;
    }

    public List<DeliveryItem> getItems() {
        return items;
    }

    public void setItems(List<DeliveryItem> items) {
        this.items = items;
    }

    public List<SuggestedDeliveryItem> getSuggestedItems() {
        return suggestedItems;
    }

    public void setSuggestedItems(List<SuggestedDeliveryItem> suggestedItems) {
        this.suggestedItems = suggestedItems;
    }

    public boolean hasDeliveryDetails() {
        return isNotBlank(externalDeliveryId) && isNotBlank(provider) && estimatedDeliveryAt != null;
    }

    public boolean hasPricesInForeignCurrency() {
        return sourceCurrency != null && !sourceCurrency.equals("PLN");
    }

    public void applyExchangeRate(double exchangeRate) {
        paymentCost = applyExchangeRate(paymentCost, exchangeRate);
        shippingCost = applyExchangeRate(shippingCost, exchangeRate);;

        for (DeliveryItem item : items) {
            item.updateUnitCost(applyExchangeRate(item.getUnitCost(), exchangeRate));
        }
    }

    private double applyExchangeRate(double amount, double exchangeRate) {
        return Price.fromNet(amount * exchangeRate).netValue();
    }

    public void applyUserSelections(DeliveryCreationForm posted) {
        setExternalDeliveryId(posted.getExternalDeliveryId());
        setEstimatedDeliveryAt(posted.getEstimatedDeliveryAt());
        setSourceCurrency(posted.getSourceCurrency());
        setShippingCost(posted.getShippingCost());
        setPaymentCost(posted.getPaymentCost());
        setPaymentTerms(posted.getPaymentTerms());
        setTax(posted.getTax());
        setRemoveUnselected(posted.isRemoveUnselected());
        setDeliveryAddressId(posted.getDeliveryAddressId());
        for (DeliveryItem postedItem : posted.getItems()) {
            DeliveryItem matchingItem = findItemByMfn(postedItem.getMfn());
            if (matchingItem != null) {
                matchingItem.setRequestedQty(postedItem.getRequestedQty());
                matchingItem.setUnitCost(postedItem.getUnitCost());
                applyAllocationSelections(matchingItem, postedItem);
            } else {
                applyToSuggestedItem(postedItem);
            }
        }
    }

    private DeliveryItem findItemByMfn(String mfn) {
        return items.stream()
                .filter(item -> Objects.equals(item.getMfn(), mfn))
                .findFirst()
                .orElse(null);
    }

    private void applyAllocationSelections(DeliveryItem matchingItem, DeliveryItem postedItem) {
        for (Allocation postedAllocation : postedItem.getAllocations()) {
            matchingItem.getAllocations().stream()
                    .filter(allocation -> sameKey(allocation.getKey(), postedAllocation.getKey()))
                    .findFirst()
                    .ifPresent(allocation -> allocation.setSelected(postedAllocation.isSelected()));
        }
    }

    private boolean sameKey(AllocationKey a, AllocationKey b) {
        return Objects.equals(a.getOrderId(), b.getOrderId()) && Objects.equals(a.getItemId(), b.getItemId());
    }

    private void applyToSuggestedItem(DeliveryItem postedItem) {
        suggestedItems.stream()
                .filter(suggested -> Objects.equals(suggested.getMfn(), postedItem.getMfn()))
                .findFirst()
                .ifPresent(suggested -> suggested.setRequestedQty(postedItem.getRequestedQty()));
    }
}
