package pl.commercelink.web.dtos;

import pl.commercelink.inventory.deliveries.InvoicePaymentSync;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderFormats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class InvoiceSyncPreview {

    private String deliveryId;
    private String externalDeliveryId;
    private String invoiceId;
    private String invoiceNumber;
    private String currency;
    private double exchangeRate;
    private double invoicePriceNet;
    private double invoicePriceGross;
    private double deliveryTotalCostNet;
    private double deliveryTotalCostGross;
    private String viewUrl;
    private double shippingCost;
    private double paymentCost;

    private String shippingCostPositionId;
    private String paymentCostPositionId;

    private String invoiceShortcut;
    private String deliveryProvider;

    private boolean invoicePaid;
    private String invoicePaymentToDate;
    private boolean deliveryPaid;
    private String deliveryPaymentDueDate;

    // read-only context of the page; not posted back
    private String deliveryShortId;
    private String deliverySupplier;
    private String supplierName;
    private String deliveryOrderedAt;
    private boolean deliveryAwaitingApproval;
    private boolean deliverySynced;
    private List<PaymentLine> deliveryPayments = new ArrayList<>();
    private Integer paymentTermDays;

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

    public double getDeliveryTotalCostNet() {
        return deliveryTotalCostNet;
    }

    public void setDeliveryTotalCostNet(double deliveryTotalCostNet) {
        this.deliveryTotalCostNet = deliveryTotalCostNet;
    }

    public double getDeliveryTotalCostGross() {
        return deliveryTotalCostGross;
    }

    public void setDeliveryTotalCostGross(double deliveryTotalCostGross) {
        this.deliveryTotalCostGross = deliveryTotalCostGross;
    }

    public boolean isPriceNetDiffers() {
        return Math.abs(invoicePriceNet - deliveryTotalCostNet) >= 0.01;
    }

    public boolean isPriceGrossDiffers() {
        return Math.abs(invoicePriceGross - deliveryTotalCostGross) >= 0.01;
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

    /** The save writes the due date only as days from the order date, so without them it changes nothing. */
    public boolean isPaymentDueDateDiffers() {
        if (invoicePaymentToDate == null || paymentTermDays == null) {
            return false;
        }
        return !invoicePaymentToDate.equals(deliveryPaymentDueDate);
    }

    /** A blank shortcut on the invoice changes nothing: the save writes only a non-blank one (InvoiceSyncService). */
    public boolean isShortcutDiffers() {
        return invoiceShortcut != null && !invoiceShortcut.isBlank() && !invoiceShortcut.equals(deliveryProvider);
    }

    public String getDeliveryShortId() {
        return deliveryShortId;
    }

    public void setDeliveryShortId(String deliveryShortId) {
        this.deliveryShortId = deliveryShortId;
    }

    public String getDeliverySupplier() {
        return deliverySupplier;
    }

    public void setDeliverySupplier(String deliverySupplier) {
        this.deliverySupplier = deliverySupplier;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getDeliveryOrderedAt() {
        return deliveryOrderedAt;
    }

    public void setDeliveryOrderedAt(String deliveryOrderedAt) {
        this.deliveryOrderedAt = deliveryOrderedAt;
    }

    public boolean isDeliveryAwaitingApproval() {
        return deliveryAwaitingApproval;
    }

    public void setDeliveryAwaitingApproval(boolean deliveryAwaitingApproval) {
        this.deliveryAwaitingApproval = deliveryAwaitingApproval;
    }

    public boolean isDeliverySynced() {
        return deliverySynced;
    }

    public void setDeliverySynced(boolean deliverySynced) {
        this.deliverySynced = deliverySynced;
    }

    public List<PaymentLine> getDeliveryPayments() {
        return deliveryPayments;
    }

    public void setDeliveryPayments(List<PaymentLine> deliveryPayments) {
        this.deliveryPayments = deliveryPayments;
    }

    public int getDeliveryPaymentsCount() {
        return deliveryPayments.size();
    }

    public Integer getPaymentTermDays() {
        return paymentTermDays;
    }

    public void setPaymentTermDays(Integer paymentTermDays) {
        this.paymentTermDays = paymentTermDays;
    }

    public InvoicePaymentSync getPaymentSync() {
        return InvoicePaymentSync.of(invoicePaid, !deliveryPayments.isEmpty());
    }

    public boolean isForeignCurrency() {
        return currency != null && !"PLN".equalsIgnoreCase(currency);
    }

    /** An amount of the delivery, kept in złoty. */
    public String money(double amount) {
        return Money.format(amount) + " PLN";
    }

    /** An amount of the invoice, in the invoice's currency. */
    public String invoiceMoney(double amount) {
        return Money.format(amount) + " " + (currency == null ? "PLN" : currency);
    }

    /** A date the page carries as ISO text, the way the order screens show dates. */
    public String date(String isoDate) {
        return isoDate == null || isoDate.isBlank() ? null : OrderFormats.date(LocalDate.parse(isoDate));
    }

    /** The price leads: a long line name is cut in the closed select, and the price is what the row is compared by. */
    public String optionLabel(Option option) {
        return invoiceMoney(option.getPriceNet()) + " \u00b7 " + option.getQty() + " \u00d7 " + option.getName();
    }

    /** The state of a product row from its current choice; invoice-sync.js applies the same rule after every change. */
    public MatchState stateOf(Mapping mapping) {
        Option option = option(mapping.getSelectedPositionId());
        return option == null ? MatchState.UNASSIGNED : MatchState.compare(option.getPriceNet(), mapping.getUnitCost());
    }

    public MatchState getShippingCostState() {
        return extraState(shippingCostPositionId, shippingCost);
    }

    public MatchState getPaymentCostState() {
        return extraState(paymentCostPositionId, paymentCost);
    }

    public long count(MatchState state) {
        return mappings.stream().filter(m -> stateOf(m) == state).count();
    }

    /** The states a product row can be in, in the order of the counts above the table. */
    public List<MatchState> getRowStates() {
        return List.of(MatchState.EXACT, MatchState.CLOSE, MatchState.DIFFERENT, MatchState.UNASSIGNED);
    }

    /** The rows whose unit cost the save changes: assigned to an invoice line of another price. */
    public long getChangedItemCount() {
        return mappings.stream().filter(m -> option(m.getSelectedPositionId()) != null && stateOf(m) != MatchState.EXACT).count();
    }

    /** The invoice positions no row has chosen, which the save leaves out of the delivery. */
    public List<Option> getUnassignedOptions() {
        Set<String> chosen = chosenPositionIds();
        return options.stream().filter(o -> !chosen.contains(o.getId())).toList();
    }

    public double getAssignedNet() {
        Set<String> chosen = chosenPositionIds();
        return options.stream().filter(o -> chosen.contains(o.getId())).mapToDouble(Option::getTotalNet).sum();
    }

    public double getUnassignedNet() {
        return getUnassignedOptions().stream().mapToDouble(Option::getTotalNet).sum();
    }

    public Option option(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        return options.stream().filter(o -> positionId.equals(o.getId())).findFirst().orElse(null);
    }

    private MatchState extraState(String positionId, double cost) {
        Option option = option(positionId);
        if (option == null) {
            return Math.abs(cost) < MatchState.EPS ? MatchState.NO_COST : MatchState.UNASSIGNED;
        }
        return MatchState.compare(option.getTotalNet(), cost);
    }

    private Set<String> chosenPositionIds() {
        Set<String> chosen = new HashSet<>();
        Stream.concat(mappings.stream().map(Mapping::getSelectedPositionId), Stream.of(shippingCostPositionId, paymentCostPositionId))
                .filter(id -> id != null && !id.isBlank())
                .forEach(chosen::add);
        return chosen;
    }

    /** How a delivery cost compares with the invoice position chosen for it; the tone is the pill's class. */
    public enum MatchState {
        EXACT("is-ok", "invoiceSync.state.exact"),
        CLOSE("is-info", "invoiceSync.state.close"),
        DIFFERENT("is-warn", "invoiceSync.state.different"),
        UNASSIGNED("is-bad", "invoiceSync.state.unassigned"),
        NO_COST("is-neutral", "invoiceSync.state.noCost");

        static final double EPS = 0.005;
        private static final double CLOSE_DELTA = 0.01;

        private final String tone;
        private final String labelKey;

        MatchState(String tone, String labelKey) {
            this.tone = tone;
            this.labelKey = labelKey;
        }

        // the bands of InvoicePositionMatcher: equal below half a grosz, "close" at one grosz either way
        static MatchState compare(double invoiceAmount, double deliveryAmount) {
            double delta = Math.abs(invoiceAmount - deliveryAmount);
            if (delta < EPS) {
                return EXACT;
            }
            return Math.abs(delta - CLOSE_DELTA) < EPS ? CLOSE : DIFFERENT;
        }

        public String getTone() {
            return tone;
        }

        public String getLabelKey() {
            return labelKey;
        }
    }

    /** A payment the delivery records, as the removal effect names it. */
    public record PaymentLine(double amount, String reference) {
    }

    public static class Option {
        private String id;
        private String name;
        private int qty;
        private double priceNet;
        private double totalNet;
        private String currency;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public double getPriceNet() {
            return priceNet;
        }

        public void setPriceNet(double priceNet) {
            this.priceNet = priceNet;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public double getTotalNet() {
            return totalNet;
        }

        public void setTotalNet(double totalNet) {
            this.totalNet = totalNet;
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
