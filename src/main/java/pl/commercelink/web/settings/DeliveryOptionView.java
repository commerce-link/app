package pl.commercelink.web.settings;

import pl.commercelink.stores.DeliveryOption;

/** A delivery option as its row on the payments page: name, how the parcel travels, gross price and description. */
public record DeliveryOptionView(String id, String name, String typeKey, double price, String description,
                                 String editHref, String deleteHref) {

    public static DeliveryOptionView of(DeliveryOption option, String optionsPath) {
        String base = optionsPath + "/" + option.getId();
        String typeKey = option.getType() != null ? "ShipmentType." + option.getType().name() : null;
        return new DeliveryOptionView(option.getId(), option.getName(), typeKey, option.getPrice(),
                option.getDescription(), base, base + "/delete");
    }
}
