package pl.commercelink.shipping;

/**
 * What the courier booking page says about the record it ships from: where "back" leads and the sentence under the
 * title. The page is shared by orders, RMA and warehouse items, so each controller describes its own record; the
 * texts are message keys with one argument, resolved by the template.
 */
public record ShippingPageView(String backHref, String backLabelKey, Object backLabelArgument,
                               String leadKey, Object leadArgument) {
}
