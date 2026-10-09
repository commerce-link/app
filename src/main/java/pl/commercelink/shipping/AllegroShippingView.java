package pl.commercelink.shipping;

/**
 * The read-only parts of the Wysyłam z Allegro form, resolved for the operator's language: the buyer's delivery
 * method, carrier and point, the recipient, the method's limits as one sentence, the help under cash on delivery and
 * insurance, the store's label format (a message key) and where it is changed.
 */
public record AllegroShippingView(String methodName, String carrierName, String pointCode, String deliveryTypeKey,
                                  String recipient, String limits, String codHelp, String insuranceHelp,
                                  String labelFormatKey, String settingsHref) {
}
