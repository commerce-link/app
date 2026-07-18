package pl.commercelink.orders.fulfilment;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FulfilmentForm {

    private String redirectUrl;
    private String fulfilmentType;
    private String pathSelector = "default";
    private boolean onlyWithProfit;
    private boolean onlyMultiOrder;
    private boolean onlyLocalSuppliers;
    private boolean orderByOrder;
    private Map<String, Double> committedSuppliers = new LinkedHashMap<>();
    private List<String> selectedOrders = new LinkedList<>();
    private List<FulfilmentGroup> entries = new LinkedList<>();
    private List<FulfilmentVariant> variants = new LinkedList<>();

    public FulfilmentForm() {

    }

    public FulfilmentForm(String fulfilmentType, String redirectUrl, List<FulfilmentGroup> entries) {
        this.fulfilmentType = fulfilmentType;
        this.redirectUrl = redirectUrl;
        this.entries = entries;
    }

    public FulfilmentForm(String fulfilmentType, String redirectUrl, List<String> selectedOrders, List<FulfilmentGroup> entries) {
        this.redirectUrl = redirectUrl;
        this.fulfilmentType = fulfilmentType;
        this.selectedOrders = selectedOrders;
        this.entries = entries;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getFulfilmentType() {
        return fulfilmentType;
    }

    public void setFulfilmentType(String fulfilmentType) {
        this.fulfilmentType = fulfilmentType;
    }

    public List<FulfilmentGroup> getEntries() {
        return entries;
    }

    public void setEntries(List<FulfilmentGroup> entries) {
        this.entries = entries;
    }

    public List<FulfilmentVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<FulfilmentVariant> variants) {
        this.variants = variants;
    }

    public String getPathSelector() {
        return pathSelector;
    }

    public void setPathSelector(String pathSelector) {
        this.pathSelector = pathSelector;
    }

    public boolean isOnlyWithProfit() {
        return onlyWithProfit;
    }

    public void setOnlyWithProfit(boolean onlyWithProfit) {
        this.onlyWithProfit = onlyWithProfit;
    }

    public boolean isOnlyMultiOrder() {
        return onlyMultiOrder;
    }

    public void setOnlyMultiOrder(boolean onlyMultiOrder) {
        this.onlyMultiOrder = onlyMultiOrder;
    }

    public boolean isOnlyLocalSuppliers() {
        return onlyLocalSuppliers;
    }

    public void setOnlyLocalSuppliers(boolean onlyLocalSuppliers) {
        this.onlyLocalSuppliers = onlyLocalSuppliers;
    }

    public boolean isOrderByOrder() {
        return orderByOrder;
    }

    public void setOrderByOrder(boolean orderByOrder) {
        this.orderByOrder = orderByOrder;
    }

    public Map<String, Double> getCommittedSuppliers() {
        return committedSuppliers;
    }

    public void setCommittedSuppliers(Map<String, Double> committedSuppliers) {
        this.committedSuppliers = committedSuppliers;
    }

    public Map<String, Double> getAcceptedValueByProvider() {
        return entries.stream()
                .filter(FulfilmentGroup::isAccepted)
                .collect(Collectors.groupingBy(
                        group -> group.getSource().getProvider(),
                        LinkedHashMap::new,
                        Collectors.summingDouble(FulfilmentGroup::getSourceValue)));
    }

    public List<String> getSelectedOrders() {
        return selectedOrders;
    }

    public void setSelectedOrders(List<String> selectedOrders) {
        this.selectedOrders = selectedOrders;
    }

    public boolean hasRemainingOrders() {
        return selectedOrders.size() > 1;
    }

    public List<String> getRemainingOrders() {
        return hasRemainingOrders() ? selectedOrders.subList(1, selectedOrders.size()) : new LinkedList<>();
    }

    public List<FulfilmentItem> getAcceptedFulfilmentItems() {
        List<FulfilmentItem> acceptedItems = new LinkedList<>();
        for (FulfilmentGroup group : entries) {
            if (group.isAccepted()) {
                acceptedItems.addAll(group.getFulfilmentItems());
            }
        }
        return acceptedItems;
    }

    public Map<String, List<FulfilmentItem>> getAcceptedFulfilmentItemsGroupedByOrderId() {
        return getAcceptedFulfilmentItems().stream()
                .collect(Collectors.groupingBy(i -> i.getAllocation().getOrderId()));
    }

}
