package pl.commercelink.web.dtos;

import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.web.orders.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private Map<String, String> supplierOrderChoices = new LinkedHashMap<>();

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

    public Map<String, String> getSupplierOrderChoices() {
        return supplierOrderChoices;
    }

    public void setSupplierOrderChoices(Map<String, String> supplierOrderChoices) {
        this.supplierOrderChoices = supplierOrderChoices == null ? new LinkedHashMap<>() : supplierOrderChoices;
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

    public boolean hasRequestedItems() {
        return items.stream().anyMatch(item -> item.getRequestedQty() > 0);
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
        // Step 1 sent back before step 2 merged them (an unreadable number): the suggestions are still suggestions.
        if (posted.getSuggestedItems() != null) {
            for (SuggestedDeliveryItem postedSuggestion : posted.getSuggestedItems()) {
                applyToSuggestedItem(postedSuggestion);
            }
        }
    }

    /** Unit costs in whole grosze: the cost fields have no browser validation, so a longer fraction is rounded here. */
    public void roundUnitCosts() {
        if (items != null) {
            items.forEach(item -> item.setUnitCost(Money.round(item.getUnitCost())));
        }
        if (suggestedItems != null) {
            suggestedItems.forEach(suggested -> suggested.setUnitCost(Money.round(suggested.getUnitCost())));
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

    // An item without sources that is not in the plan was a suggestion merged by step 2; one with sources left the plan.
    private void applyToSuggestedItem(DeliveryItem postedItem) {
        if (postedItem.getAllocations() == null || postedItem.getAllocations().isEmpty()) {
            applyToSuggestedItem(postedItem.getMfn(), postedItem.getName(), postedItem.getEan(),
                    postedItem.getRequestedQty(), postedItem.getUnitCost());
        }
    }

    private void applyToSuggestedItem(SuggestedDeliveryItem posted) {
        applyToSuggestedItem(posted.getMfn(), posted.getName(), posted.getEan(), posted.getRequestedQty(), posted.getUnitCost());
    }

    /**
     * The typed quantity and cost of a suggestion. The plan carries no suggestions (the page fetches them later), so
     * one the operator chose comes back as a row of its own and the fetched list takes its typed values over.
     */
    private void applyToSuggestedItem(String mfn, String name, String ean, int requestedQty, double unitCost) {
        SuggestedDeliveryItem suggested = suggestedItems == null ? null : suggestedItems.stream()
                .filter(candidate -> Objects.equals(candidate.getMfn(), mfn))
                .findFirst()
                .orElse(null);
        if (suggested == null) {
            if (requestedQty <= 0 || mfn == null) {
                return;
            }
            if (suggestedItems == null) {
                suggestedItems = new ArrayList<>();
            }
            suggested = new SuggestedDeliveryItem();
            suggested.setMfn(mfn);
            suggested.setName(name);
            suggested.setEan(ean);
            suggestedItems.add(suggested);
        }
        suggested.setRequestedQty(requestedQty);
        suggested.setUnitCost(unitCost);
    }
}
