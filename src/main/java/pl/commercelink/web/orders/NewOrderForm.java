package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.Basket;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.ClientDataDto;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the "new order from a basket" page posts, checked on the server (the page used to rely on browser scripts,
 * one of which copied the billing address into the shipping one). Only a courier delivery needs a street, a postal
 * code, a city and a country; a pickup point and a personal collection need the customer's name and contact only.
 * The checks reuse {@link OrderAddressForm}, so the messages and field names match the order's address forms.
 */
public record NewOrderForm(ClientDataDto dto, String shippingAddress) {

    public static final String SAME = "same";
    public static final String OTHER = "other";

    private static final Pattern COUNTRY = Pattern.compile("[A-Za-z]{2}");
    private static final Set<String> ADDRESS_REQUIRED_KEYS = Set.of("billing.street.required",
            "billing.postalCode.required", "billing.city.required", "billing.country.required");

    /** Missing parts of the request count as empty ones, so a disabled field never causes a NullPointerException. */
    public static NewOrderForm of(ClientDataDto dto, String shippingAddress) {
        ClientDataDto source = dto != null ? dto : new ClientDataDto();
        ClientDataDto complete = new ClientDataDto();
        complete.setOrderReference(source.getOrderReference());
        complete.setShipmentType(source.getShipmentType());
        complete.setPaymentSource(source.getPaymentSource());
        complete.setBillingDetails(source.getBillingDetails() != null ? source.getBillingDetails() : new BillingDetails());
        complete.setShippingDetails(source.getShippingDetails() != null ? source.getShippingDetails() : new ShippingDetails());
        return new NewOrderForm(complete, OTHER.equals(shippingAddress) ? OTHER : SAME);
    }

    public boolean shipsToOtherAddress() {
        return dto.getShipmentType() == ShipmentType.Courier && OTHER.equals(shippingAddress);
    }

    public boolean needsShippingAddress() {
        return shipsToOtherAddress();
    }

    public OrderAddressForm billing() {
        return OrderAddressForm.billing(null, dto.getBillingDetails());
    }

    public OrderAddressForm shipping() {
        return OrderAddressForm.shipping(null, dto.getShippingDetails());
    }

    /** Field name to message key, in the order of the page. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (dto.getShipmentType() == null) {
            errors.put("shipmentType", "order.new.shipment.required");
        }
        if (dto.getPaymentSource() == null) {
            errors.put("paymentSource", "order.new.payment.required");
        }

        Map<String, String> billing = new LinkedHashMap<>(billing().validate());
        if (dto.getShipmentType() != ShipmentType.Courier) {
            billing.values().removeIf(ADDRESS_REQUIRED_KEYS::contains);
        }
        errors.putAll(billing);

        if (needsShippingAddress()) {
            errors.putAll(shipping().validate());
        } else if (dto.getShipmentType() == ShipmentType.PickupPoint) {
            errors.putAll(shippingFormats());
        }
        return errors;
    }

    /** The data for {@code BasketOrderImporter}; call it only when {@link #validate()} found nothing. */
    public ClientDataDto toImport(Basket basket) {
        ClientDataDto result = new ClientDataDto();
        result.setOrderReference(dto.getOrderReference());
        result.setShipmentType(dto.getShipmentType());
        result.setPaymentSource(dto.getPaymentSource());

        BillingDetails billing = dto.getBillingDetails().copy();
        billing.setCountry(normalizedCountry(billing.getCountry()));
        result.setBillingDetails(billing);
        result.setShippingDetails(shippingToImport(billing, basket));
        return result;
    }

    private ShippingDetails shippingToImport(BillingDetails billing, Basket basket) {
        ShipmentType type = dto.getShipmentType();
        if (type == ShipmentType.Courier && shipsToOtherAddress()) {
            return postedShipping();
        }
        if (type == ShipmentType.PickupPoint && !isShippingEmptyExceptCountry()) {
            return postedShipping();
        }
        if (type == ShipmentType.Courier || type == ShipmentType.PickupPoint) {
            return copyOf(billing);
        }
        return basket != null && basket.getShippingDetails() != null
                ? basket.getShippingDetails()
                : ShippingDetails._default();
    }

    private ShippingDetails postedShipping() {
        ShippingDetails shipping = dto.getShippingDetails().copy();
        shipping.setCountry(normalizedCountry(shipping.getCountry()));
        return shipping;
    }

    private static ShippingDetails copyOf(BillingDetails billing) {
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName(billing.getName());
        shipping.setSurname(billing.getSurname());
        shipping.setCompanyName(billing.getCompanyName());
        shipping.setStreetAndNumber(billing.getStreetAndNumber());
        shipping.setPostalCode(billing.getPostalCode());
        shipping.setCity(billing.getCity());
        shipping.setCountry(billing.getCountry());
        shipping.setEmail(billing.getEmail());
        shipping.setPhone(billing.getPhone());
        return shipping;
    }

    private boolean isShippingEmptyExceptCountry() {
        ShippingDetails s = dto.getShippingDetails();
        return StringUtils.isAllBlank(s.getName(), s.getSurname(), s.getCompanyName(), s.getStreetAndNumber(),
                s.getPostalCode(), s.getCity(), s.getEmail(), s.getPhone());
    }

    /** A pickup point requires nothing, but what was typed must still be usable. */
    private Map<String, String> shippingFormats() {
        Map<String, String> found = new LinkedHashMap<>();
        ShippingDetails s = dto.getShippingDetails();
        OrderAddressForm form = shipping();
        if (StringUtils.isNotBlank(s.getCountry()) && !COUNTRY.matcher(s.getCountry().trim()).matches()) {
            found.put(form.field("country"), "order.address.country.invalid");
        }
        if (StringUtils.isNotBlank(s.getEmail()) && !OrderAddressForm.isEmail(s.getEmail())) {
            found.put(form.field("email"), "billing.email.invalid");
        }
        if (StringUtils.isNotBlank(s.getPhone()) && !OrderAddressForm.isPhone(s.getPhone())) {
            found.put(form.field("phone"), "billing.phone.invalid");
        }
        return found;
    }

    private static String normalizedCountry(String country) {
        return StringUtils.trimToEmpty(country).toUpperCase(Locale.ROOT);
    }
}
