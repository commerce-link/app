package pl.commercelink.web.offers;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The terms dialog of the offer details page. A template has only the name, "show prices" and the fulfilment type;
 * the other fields are ignored for it, as its old form never carried them.
 */
@Getter
@Setter
public class OfferTermsForm {

    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private String name;
    private LocalDateTime expiresAt;
    private String deliveryOptionId;
    private boolean showPrices;
    private FulfilmentType fulfilmentType;
    private String gclid;

    public static OfferTermsForm from(Basket basket) {
        OfferTermsForm form = new OfferTermsForm();
        form.name = basket.getName();
        form.expiresAt = basket.getExpiresAt();
        form.deliveryOptionId = basket.getDeliveryOptionId();
        form.showPrices = basket.isShowPrices();
        form.fulfilmentType = basket.getFulfilmentType();
        form.gclid = basket.getGclid();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.isBlank(name)) {
            errors.put("name", "offers.name.required");
        }
        return errors;
    }

    /** The value of the datetime-local input. */
    public String getExpiresAtInput() {
        return expiresAt == null ? "" : INPUT.format(expiresAt);
    }

    void applyTo(Basket basket) {
        basket.setName(name);
        basket.setShowPrices(showPrices);
        if (fulfilmentType != null) {
            basket.setFulfilmentType(fulfilmentType);
        }
        if (!basket.hasType(BasketType.OfferTemplate)) {
            basket.setExpiresAt(expiresAt);
            basket.setDeliveryOptionId(deliveryOptionId);
            basket.setGclid(gclid);
        }
    }
}
