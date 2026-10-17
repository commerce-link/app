package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.ClientDataDto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What the "new order from a basket or an offer" page shows besides the form: where the order comes from, the way
 * back, and a summary of what will be taken over. Also the starting values of the form.
 */
public record NewOrderPage(String basketId, boolean offer, String backHref, String sourceName,
                           int itemsCount, DeliveryState deliveryState, String deliveryName, double deliveryPrice,
                           ShipmentType deliveryType, double total, boolean billingFromContact) {

    /** UNKNOWN: the basket points at a delivery option the store no longer has. */
    public enum DeliveryState { CHOSEN, NONE, UNKNOWN }

    public static NewOrderPage of(Basket basket, Store store) {
        // Importing a template would delete it, and no screen leads here from one.
        if (basket.hasType(BasketType.OfferTemplate)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        boolean offer = basket.hasType(BasketType.Offer);
        String id = basket.getBasketId();
        String encodedId = UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
        String backHref = offer ? "/dashboard/offer/" + encodedId : "/dashboard/basket/view/" + encodedId;
        String sourceName = offer && StringUtils.isNotBlank(basket.getName())
                ? basket.getName() : basket.getShortenedBasketId();

        DeliveryState state = DeliveryState.NONE;
        String deliveryName = null;
        double deliveryPrice = 0;
        ShipmentType deliveryType = null;
        if (basket.getDeliveryOptionId() != null) {
            Optional<DeliveryOption> option = resolve(basket, store);
            if (option.isPresent()) {
                state = DeliveryState.CHOSEN;
                deliveryName = option.get().getName();
                deliveryPrice = option.get().getPrice();
                deliveryType = option.get().getType();
            } else {
                state = DeliveryState.UNKNOWN;
            }
        }

        return new NewOrderPage(id, offer, backHref, sourceName, basket.getEffectiveBasketItems().size(),
                state, deliveryName, deliveryPrice, deliveryType, basket.getTotalPrice() + deliveryPrice,
                billingFromContact(basket));
    }

    public static ClientDataDto initialForm(Basket basket, Store store) {
        ClientDataDto form = new ClientDataDto();
        form.setOrderReference(basket.getBasketId());
        form.setShipmentType(resolve(basket, store).map(DeliveryOption::getType).orElse(ShipmentType.Courier));
        form.setPaymentSource(PaymentSource.BankTransfer);
        form.setBillingDetails(initialBilling(basket));
        form.setShippingDetails(basket.getShippingDetails() != null
                ? basket.getShippingDetails() : ShippingDetails._default());
        return form;
    }

    public static String initialShippingAddress(Basket basket) {
        ShippingDetails shipping = basket.getShippingDetails();
        if (shipping == null || isEmptyIgnoringCountry(shipping)) {
            return NewOrderForm.SAME;
        }
        BillingDetails billing = basket.getBillingDetails();
        List<String> billingParts = billing != null ? billingParts(billing) : List.of();
        return sameParts(shippingParts(shipping), billingParts) ? NewOrderForm.SAME : NewOrderForm.OTHER;
    }

    public boolean hasDeliveryOption() {
        return deliveryState == DeliveryState.CHOSEN;
    }

    // An option removed from the store throws here; the page must still open and say so.
    private static Optional<DeliveryOption> resolve(Basket basket, Store store) {
        try {
            return basket.resolveDeliveryOption(store);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static BillingDetails initialBilling(Basket basket) {
        if (basket.getBillingDetails() != null) {
            return basket.getBillingDetails();
        }
        BillingDetails billing = BillingDetails._default();
        if (hasContact(basket)) {
            ContactDetails contact = basket.getContactDetails();
            billing.setName(contact.getName());
            billing.setSurname(contact.getSurname());
            billing.setCompanyName(contact.getCompanyName());
            billing.setEmail(contact.getEmail());
            billing.setPhone(contact.getPhone());
        }
        return billing;
    }

    private static boolean billingFromContact(Basket basket) {
        return basket.getBillingDetails() == null && hasContact(basket);
    }

    private static boolean hasContact(Basket basket) {
        ContactDetails c = basket.getContactDetails();
        return c != null && (StringUtils.isNotBlank(c.getName()) || StringUtils.isNotBlank(c.getSurname())
                || StringUtils.isNotBlank(c.getCompanyName()) || StringUtils.isNotBlank(c.getEmail())
                || StringUtils.isNotBlank(c.getPhone()));
    }

    private static List<String> shippingParts(ShippingDetails d) {
        return Arrays.asList(d.getName(), d.getSurname(), d.getCompanyName(), d.getStreetAndNumber(),
                d.getPostalCode(), d.getCity(), d.getCountry(), d.getEmail(), d.getPhone());
    }

    private static List<String> billingParts(BillingDetails d) {
        return Arrays.asList(d.getName(), d.getSurname(), d.getCompanyName(), d.getStreetAndNumber(),
                d.getPostalCode(), d.getCity(), d.getCountry(), d.getEmail(), d.getPhone());
    }

    // The country is prefilled by ShippingDetails._default(), so it alone does not make an address filled in.
    private static boolean isEmptyIgnoringCountry(ShippingDetails d) {
        return Arrays.asList(d.getName(), d.getSurname(), d.getCompanyName(), d.getStreetAndNumber(),
                d.getPostalCode(), d.getCity(), d.getEmail(), d.getPhone()).stream().allMatch(StringUtils::isBlank);
    }

    private static boolean sameParts(List<String> a, List<String> b) {
        for (int i = 0; i < a.size(); i++) {
            String left = StringUtils.trimToEmpty(a.get(i));
            String right = i < b.size() ? StringUtils.trimToEmpty(b.get(i)) : "";
            if (!left.equalsIgnoreCase(right)) {
                return false;
            }
        }
        return true;
    }
}
