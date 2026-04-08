package pl.commercelink.web.dtos;

import java.util.ArrayList;
import java.util.List;

public class InvoiceSyncPreview {

    private String deliveryId;
    private String externalDeliveryId;
    private String invoiceId;
    private String invoiceNumber;
    private String currency;
    private double exchangeRate;
    private double invoicePriceNet;
    private double invoicePriceGross;
    private String viewUrl;
    private double shippingCost;
    private double paymentCost;

    private String shippingCostPositionId;
    private String paymentCostPositionId;

    private String invoiceShortcut;
    private String deliveryProvider;

    private boolean invoicePaid;
    private String invoicePaymentToDate;
    private String deliveryPaymentStatus;
    private boolean deliveryPaid;
    private String deliveryPaymentDueDate;

    private List<Option> options = new ArrayList<>();
    private List<Mapping> mappings = new ArrayList<>();

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getExternalDeliveryId() {
        return externalDeliveryId;
    }

    public void setExternalDeliveryId(String externalDeliveryId) {
        this.externalDeliveryId = externalDeliveryId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(double exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public double getInvoicePriceNet() {
        return invoicePriceNet;
    }

    public void setInvoicePriceNet(double invoicePriceNet) {
        this.invoicePriceNet = invoicePriceNet;
    }

    public double getInvoicePriceGross() {
        return invoicePriceGross;
    }

    public void setInvoicePriceGross(double invoicePriceGross) {
        this.invoicePriceGross = invoicePriceGross;
    }

    public String getViewUrl() {
        return viewUrl;
    }

    public void setViewUrl(String viewUrl) {
        this.viewUrl = viewUrl;
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

    public List<Option> getOptions() {
        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    public List<Mapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public String getShippingCostPositionId() {
        return shippingCostPositionId;
    }

    public void setShippingCostPositionId(String shippingCostPositionId) {
        this.shippingCostPositionId = shippingCostPositionId;
    }

    public String getPaymentCostPositionId() {
        return paymentCostPositionId;
    }

    public void setPaymentCostPositionId(String paymentCostPositionId) {
        this.paymentCostPositionId = paymentCostPositionId;
    }

    public String getInvoiceShortcut() {
        return invoiceShortcut;
    }

    public void setInvoiceShortcut(String invoiceShortcut) {
        this.invoiceShortcut = invoiceShortcut;
    }

    public String getDeliveryProvider() {
        return deliveryProvider;
    }

    public void setDeliveryProvider(String deliveryProvider) {
        this.deliveryProvider = deliveryProvider;
    }

    public boolean isInvoicePaid() {
        return invoicePaid;
    }

    public void setInvoicePaid(boolean invoicePaid) {
        this.invoicePaid = invoicePaid;
    }

    public String getInvoicePaymentToDate() {
        return invoicePaymentToDate;
    }

    public void setInvoicePaymentToDate(String invoicePaymentToDate) {
        this.invoicePaymentToDate = invoicePaymentToDate;
    }

    public String getDeliveryPaymentStatus() {
        return deliveryPaymentStatus;
    }

    public void setDeliveryPaymentStatus(String deliveryPaymentStatus) {
        this.deliveryPaymentStatus = deliveryPaymentStatus;
    }

    public boolean isDeliveryPaid() {
        return deliveryPaid;
    }

    public void setDeliveryPaid(boolean deliveryPaid) {
        this.deliveryPaid = deliveryPaid;
    }

    public String getDeliveryPaymentDueDate() {
        return deliveryPaymentDueDate;
    }

    public void setDeliveryPaymentDueDate(String deliveryPaymentDueDate) {
        this.deliveryPaymentDueDate = deliveryPaymentDueDate;
    }

    public boolean isPaymentStatusDiffers() {
        return invoicePaid != deliveryPaid;
    }

    public boolean isPaymentDueDateDiffers() {
        if (invoicePaymentToDate == null) {
            return false;
        }
        return !invoicePaymentToDate.equals(deliveryPaymentDueDate);
    }

    public boolean isShortcutDiffers() {
        return invoiceShortcut != null && !invoiceShortcut.equals(deliveryProvider);
    }

    public static class Option {
        private String id;
        private String label;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class Mapping {
        private String mfn;
        private String name;
        private int qty;
        private double unitCost;
        private String selectedPositionId;

        public String getMfn() {
            return mfn;
        }

        public void setMfn(String mfn) {
            this.mfn = mfn;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQty() {
            return qty;
        }

        public void setQty(int qty) {
            this.qty = qty;
        }

        public double getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(double unitCost) {
            this.unitCost = unitCost;
        }

        public String getSelectedPositionId() {
            return selectedPositionId;
        }

        public void setSelectedPositionId(String selectedPositionId) {
            this.selectedPositionId = selectedPositionId;
        }
    }
}
