package pl.commercelink.orders.fulfilment;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FulfilmentForm {

    private String redirectUrl;
    private String fulfilmentType;
    private List<String> selectedOrders = new LinkedList<>();
    private List<FulfilmentGroup> entries = new LinkedList<>();

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

    public List<String> getSelectedOrders() {
        return selectedOrders;
    }

    public void setSelectedOrders(List<String> selectedOrders) {
        this.selectedOrders = selectedOrders;
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
