package pl.commercelink.web.clientoffer;

import pl.commercelink.web.dtos.PaymentOptionView;

import java.util.List;

/**
 * Everything the public offer page shows, already decided on the server: amounts are formatted numbers without a
 * currency (the template adds it), the customer's saved data is split into display parties, and payment is unlocked
 * only once that data was given.
 */
public record ClientOfferPage(String offerId, String shortId, String offerPath, String validUntil, boolean expired,
                              boolean canEdit, boolean showPrices, List<Line> lines, Delivery delivery,
                              boolean hasGroups, String total, String vat, String net, boolean submitted,
                              Submitted data, Payment payment) {

    public record Line(String name, String category, int qty, String unitPrice, String amount, String groupId,
                       List<Option> options) {

        public boolean isGroup() {
            return groupId != null && options.size() > 1;
        }
    }

    /**
     * {@code delta} is signed against the selected option ("+ 1 000,00", "− 450,00"); null for the selected one and for
     * an option of the same price ({@code samePrice}), which would otherwise read "+ 0,00".
     */
    public record Option(int position, String name, boolean selected, boolean samePrice, String delta) {
    }

    public record Delivery(String name, String description, String price, boolean personalCollection) {
    }

    public record Submitted(Party billing, ShippingMode shippingMode, Party shipping) {
    }

    public record Party(String fullName, String companyName, String taxId, String street, String postalCode,
                        String city, String country, String email, String phone) {
    }

    public enum ShippingMode { SAME, PERSONAL_COLLECTION, OTHER }

    public record Payment(boolean unlocked, boolean splitPayment, List<PaymentOptionView> options,
                          String defaultOption, Bank bank, String billingEmail) {
    }

    /** {@code currency} is null for PLN or an empty value: the page shows it only for foreign-currency accounts. */
    public record Bank(String holder, String ibanGrouped, String ibanRaw, String transferTitle, String bankName,
                       String swift, String currency) {
    }
}
