package pl.commercelink.web.dtos;

import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.PaymentStatus;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class DeliveryCreationForm {

    private String storeId;
    private String provider;

    private String externalDeliveryId;
    private String sourceCurrency = "PLN";
    private PaymentStatus paymentStatus = PaymentStatus.New;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate estimatedDeliveryAt;
    private double shippingCost;
    private double paymentCost;
    private int paymentTerms;
    private boolean removeUnselected;

    private List<DeliveryItem> items = new ArrayList<>();

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

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
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
}
