package pl.commercelink.orders;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public record SplitGroupComponent(String sku, String name, int qty, double price) {

    public boolean isValid() {
        return isNotBlank(sku) && isNotBlank(name) && qty > 0 && price >= 0;
    }

    public double totalPrice() {
        return price * qty;
    }

}
