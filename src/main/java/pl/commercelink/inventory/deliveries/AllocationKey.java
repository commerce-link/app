package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;

public class AllocationKey {

    private String orderId;
    private String itemId;
    private String name;

    public AllocationKey() {
    }

    public AllocationKey(String orderId, String itemId, String name) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.name = StringUtils.isBlank(name) ? "Warehouse" : name.split("@")[0];
    }

    public String getId() {
        return orderId + ":" + itemId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRefName() {
        return StringUtils.isBlank(orderId) ? "Warehouse" : orderId.split("-")[0];
    }

    public String getUrl() {
        return StringUtils.isBlank(orderId) ? "/dashboard/warehouse/items/" + itemId : "/dashboard/orders/" + orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AllocationKey that = (AllocationKey) o;

        if (!orderId.equals(that.orderId)) return false;
        return itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        int result = orderId.hashCode();
        result = 31 * result + itemId.hashCode();
        return result;
    }
}
