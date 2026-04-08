package pl.commercelink.orders;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import pl.commercelink.shipping.ParcelForm;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ShippingForm {

    private String shippingEntityId; //orderId or rmaId
    private String shippingEntityType;
    private String pickUpAddressId;
    private String packageTemplateId;
    private String serviceId;
    private boolean saturdayDelivery;
    private List<ParcelForm> parcels = new LinkedList<>();
    private List<String> orderItemIds = new LinkedList<>(); //used only for RMA shipping
    private ShippingDetails shippingDetails;
    private boolean toClient;
    private boolean cashOnDelivery;
    private double cashOnDeliveryAmount;

    private ShippingForm() {}

    public ShippingForm(String shippingEntityId, String shippingEntityType) {
        this.shippingEntityId = shippingEntityId;
        this.shippingEntityType = shippingEntityType;
    }

    public String getShippingAction() {
        return createActionUrl("shipping");
    }

    public String getShippingTemplateAction() {
        return createActionUrl("shipping/template");
    }

    public String getShippingEstimateAction() {
        return createActionUrl("shipping/estimate");
    }

    public String getShippingCreateAction() {
        return createActionUrl("shipping/create");
    }

    private String createActionUrl(String action) {
        String url = "/dashboard/" + shippingEntityType + "/";
        if (StringUtils.isNotBlank(shippingEntityId)) {
            url += shippingEntityId + "/";
        }
        url += action;
        return url;
    }

    public String getShippingEntityId() {
        return shippingEntityId;
    }

    public void setShippingEntityId(String shippingEntityId) {
        this.shippingEntityId = shippingEntityId;
    }

    public String getPickUpAddressId() {
        return pickUpAddressId;
    }

    public void setPickUpAddressId(String pickUpAddressId) {
        this.pickUpAddressId = pickUpAddressId;
    }

    public String getPackageTemplateId() {
        return packageTemplateId;
    }

    public void setPackageTemplateId(String packageTemplateId) {
        this.packageTemplateId = packageTemplateId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public List<ParcelForm> getParcels() {
        return parcels;
    }

    public List<ParcelForm> getCompleteParcels() {
        return parcels.stream().filter(ParcelForm::isComplete).collect(Collectors.toList());
    }

    public void setParcels(List<ParcelForm> parcels) {
        this.parcels = parcels;
    }

    public boolean isSaturdayDelivery() {
        return saturdayDelivery;
    }

    public void setSaturdayDelivery(boolean saturdayDelivery) {
        this.saturdayDelivery = saturdayDelivery;
    }

    public List<String> getOrderItemIds() {
        return orderItemIds;
    }

    public void setOrderItemIds(List<String> orderItemIds) {
        this.orderItemIds = orderItemIds;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public boolean isToClient() {
        return toClient;
    }

    public void setToClient(boolean toClient) {
        this.toClient = toClient;
    }

    public String getShippingEntityType() {
        return shippingEntityType;
    }

    public void setShippingEntityType(String shippingEntityType) {
        this.shippingEntityType = shippingEntityType;
    }

    public boolean isCashOnDelivery() {
        return cashOnDelivery;
    }

    public void setCashOnDelivery(boolean cashOnDelivery) {
        this.cashOnDelivery = cashOnDelivery;
    }

    public double getCashOnDeliveryAmount() {
        return cashOnDeliveryAmount;
    }

    public void setCashOnDeliveryAmount(double cashOnDeliveryAmount) {
        this.cashOnDeliveryAmount = cashOnDeliveryAmount;
    }
}
