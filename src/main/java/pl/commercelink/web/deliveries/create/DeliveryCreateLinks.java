package pl.commercelink.web.deliveries.create;

import org.springframework.web.util.UriUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Addresses of the create-delivery steps for one viewer and one supplier: the store's own routes (/dashboard/...) or
 * the super admin's store-scoped ones (/dashboard/store/{storeId}/...). orderId is set for a dropship delivery (one
 * order at one supplier); from is "order" when the page was opened from the order, so its back link leads there.
 * POST addresses carry no query: the order and from travel in hidden fields of the step forms.
 */
public record DeliveryCreateLinks(String base, String provider, String orderId, String from) {

    public static final String FROM_ORDER = "order";

    public static DeliveryCreateLinks of(boolean superAdmin, String storeId, String provider, String orderId,
                                         String from) {
        String base = superAdmin ? "/dashboard/store/" + storeId : "/dashboard";
        String order = orderId == null || orderId.isBlank() ? null : orderId;
        return new DeliveryCreateLinks(base, provider, order,
                order != null && FROM_ORDER.equals(from) ? FROM_ORDER : null);
    }

    public boolean dropship() {
        return orderId != null;
    }

    public boolean fromOrder() {
        return FROM_ORDER.equals(from);
    }

    /** Step 1, the only GET of the flow; the order and its origin ride in the query. */
    public String items() {
        if (orderId == null) {
            return root();
        }
        return root() + "?order=" + encodeQueryParam(orderId) + (fromOrder() ? "&from=" + FROM_ORDER : "");
    }

    public String purchase() {
        return root() + "/purchase";
    }

    public String validate() {
        return root() + "/purchase/validate";
    }

    public String confirm() {
        return root() + "/purchase/confirm";
    }

    public String manual() {
        return root() + "/manual";
    }

    public String save() {
        return root() + "/manual/save";
    }

    public String back() {
        return root() + "/back";
    }

    public String fulfilment() {
        return root() + "/fulfilment";
    }

    public String order() {
        return base + "/orders/" + orderId;
    }

    public String preview() {
        return base + "/deliveries/preview";
    }

    public String backHref() {
        return fromOrder() ? order() : preview();
    }

    public String deliveryDetails(String deliveryId) {
        return base + "/deliveries/details?deliveryId=" + deliveryId;
    }

    private String root() {
        return base + "/deliveries/create/" + UriUtils.encodePathSegment(provider, UTF_8);
    }

    private static String encodeQueryParam(String value) {
        // UriUtils.encodeQueryParam encodes & as %26, which is correct for query params
        String encoded = UriUtils.encodeQueryParam(value, UTF_8);
        // If it somehow doesn't encode &, fall back to URLEncoder as per the brief
        if (encoded.contains("&")) {
            return URLEncoder.encode(value, UTF_8).replace("+", "%20");
        }
        return encoded;
    }
}
