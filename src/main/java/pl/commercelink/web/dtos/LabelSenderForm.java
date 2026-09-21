package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.orders.ShippingDetails;

import java.util.Map;

/**
 * Who the label names as the sender: the pickup address of the shipment, or other details (a head office, a brand name).
 * The address fields are checked only for other details; with the pickup address they are kept in the form but not saved.
 */
@Getter
@Setter
public class LabelSenderForm extends ShippingAddressForm {

    public static final String PICKUP = "PICKUP";
    public static final String OTHER = "OTHER";

    private String sender = PICKUP;

    public static LabelSenderForm from(ShippingDetails saved) {
        LabelSenderForm form = new LabelSenderForm();
        ShippingAddressForm address = saved == null ? ShippingAddressForm.empty() : ShippingAddressForm.from(saved);
        form.setCompanyName(address.getCompanyName());
        form.setContactPerson(address.getContactPerson());
        form.setStreetAndNumber(address.getStreetAndNumber());
        form.setPostalCode(address.getPostalCode());
        form.setCity(address.getCity());
        form.setCountry(address.getCountry());
        form.setEmail(address.getEmail());
        form.setPhone(address.getPhone());
        form.sender = saved == null ? PICKUP : OTHER;
        return form;
    }

    public boolean other() {
        return OTHER.equals(sender);
    }

    @Override
    public Map<String, String> validate() {
        return other() ? super.validate() : Map.of();
    }

    /** The sender to store: null prints the pickup address; otherwise the saved sender edited in place (same id). */
    public ShippingDetails toSender(ShippingDetails saved) {
        if (!other()) {
            return null;
        }
        ShippingDetails sender = saved != null ? saved : new ShippingDetails();
        applyTo(sender);
        return sender;
    }
}
