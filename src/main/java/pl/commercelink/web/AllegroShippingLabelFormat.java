package pl.commercelink.web;

/**
 * Label format of Wysyłam z Allegro, stored as text in the adapter's "labelFormat" setting. The app does not compile
 * against the adapter, so the values mirror pl.commercelink.shipping.allegro.AllegroLabelFormat by name.
 */
public enum AllegroShippingLabelFormat {
    PDF_A6,
    PDF_A4,
    ZPL;

    public static AllegroShippingLabelFormat of(String value) {
        if (value == null) {
            return PDF_A6;
        }
        for (AllegroShippingLabelFormat format : values()) {
            if (format.name().equals(value)) {
                return format;
            }
        }
        return PDF_A6;
    }

    /** Message key of the format's name ("PDF A6"). */
    public String messageKey() {
        return "shipping.allegro.labelFormat." + name();
    }
}
