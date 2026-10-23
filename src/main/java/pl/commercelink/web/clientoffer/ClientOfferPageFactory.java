package pl.commercelink.web.clientoffer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.invoicing.api.SplitPaymentPolicy;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Iban;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.clientoffer.ClientOfferPage.ShippingMode;
import pl.commercelink.web.dtos.ClientOfferLine;
import pl.commercelink.web.dtos.PaymentOptionView;
import pl.commercelink.web.orders.Money;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Component
@RequiredArgsConstructor
public class ClientOfferPageFactory {

    private static final String VALID_UNTIL_PATTERN = "d MMMM yyyy, HH:mm";
    private static final int SHORT_ID_LENGTH = 8;
    private static final String VARIANT_ANCHOR = "variant-";

    private final PaymentProviderFactory paymentProviderFactory;

    public ClientOfferPage build(Basket basket, Store store, boolean canEdit, Locale locale) {
        List<ClientOfferLine> offerLines = ClientOfferLine.from(basket);
        DeliveryOption deliveryOption = resolveDelivery(basket, store);
        double deliveryPrice = deliveryOption != null ? deliveryOption.getPrice() : 0.0;
        double total = basket.getTotalPrice() + deliveryPrice;
        Price gross = Price.fromGross(total, DEFAULT_VAT_RATE);
        boolean submitted = basket.getBillingDetails() != null && basket.getShippingDetails() != null;
        ClientOfferPage.Delivery delivery = deliveryOption == null ? null : new ClientOfferPage.Delivery(
                deliveryOption.getName(), deliveryOption.getDescription(), Money.format(deliveryPrice),
                deliveryOption.getType() == ShipmentType.PersonalCollection);

        return new ClientOfferPage(
                basket.getBasketId(),
                shortId(basket.getBasketId()),
                basket.createOfferUrl(""),
                basket.getExpiresAt() == null ? null
                        : basket.getExpiresAt().format(DateTimeFormatter.ofPattern(VALID_UNTIL_PATTERN, locale)),
                basket.isExpired(),
                canEdit,
                basket.isShowPrices(),
                IntStream.range(0, offerLines.size()).mapToObj(i -> toLine(offerLines.get(i), i)).toList(),
                delivery,
                offerLines.stream().anyMatch(ClientOfferLine::isGroup),
                Money.format(total),
                Money.format(total - gross.netValue()),
                Money.format(gross.netValue()),
                submitted,
                submitted ? toSubmitted(basket, delivery) : null,
                toPayment(basket, store, submitted));
    }

    // The store may have no checkout configuration at all; the basket then cannot resolve its delivery option.
    private DeliveryOption resolveDelivery(Basket basket, Store store) {
        if (basket.getDeliveryOptionId() == null || store.getCheckoutConfiguration() == null) {
            return null;
        }
        return basket.resolveDeliveryOption(store).orElse(null);
    }

    /**
     * The id of the variant group's row, by its place among the offer lines: a group name is free text (spaces, Polish
     * letters, anything typed in the dashboard), so it cannot be an id itself. Empty for an unknown group.
     */
    public static Optional<String> variantAnchor(Basket basket, String groupId) {
        List<ClientOfferLine> lines = ClientOfferLine.from(basket);
        return IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).isGroup() && lines.get(i).groupId().equals(groupId))
                .mapToObj(i -> VARIANT_ANCHOR + i)
                .findFirst();
    }

    private ClientOfferPage.Line toLine(ClientOfferLine line, int index) {
        BasketItem item = line.item();
        List<ClientOfferPage.Option> options = line.options().stream()
                .map(o -> {
                    boolean samePrice = !o.selected() && o.isIncluded();
                    return new ClientOfferPage.Option(o.item().getPosition(), o.item().getName(), o.selected(),
                            samePrice, o.selected() || samePrice ? null : signedDelta(o));
                })
                .toList();
        return new ClientOfferPage.Line(item.getName(), item.getCategory(), (int) item.getQty(),
                Money.format(item.getUnitPrice()), Money.format(item.getTotalPrice()),
                line.isGroup() ? line.groupId() : null, line.isGroup() ? options : List.of(),
                line.isGroup() ? VARIANT_ANCHOR + index : null);
    }

    private String signedDelta(ClientOfferLine.Option option) {
        String sign = option.priceDelta() < 0 ? "−" : "+";
        return sign + " " + Money.format(option.absPriceDelta());
    }

    private ClientOfferPage.Submitted toSubmitted(Basket basket, ClientOfferPage.Delivery delivery) {
        BillingDetails billing = basket.getBillingDetails();
        ShippingDetails shipping = basket.getShippingDetails();
        ShippingMode mode;
        if (delivery != null && delivery.personalCollection()) {
            mode = ShippingMode.PERSONAL_COLLECTION;
        } else if (sameAddress(shipping, ClientOfferDataForm.shippingCopyOf(billing))) {
            mode = ShippingMode.SAME;
        } else {
            mode = ShippingMode.OTHER;
        }
        return new ClientOfferPage.Submitted(
                new ClientOfferPage.Party(fullName(billing.getName(), billing.getSurname()), billing.getCompanyName(),
                        billing.getTaxId(), billing.getStreetAndNumber(), billing.getPostalCode(), billing.getCity(),
                        billing.getCountry(), billing.getEmail(), billing.getPhone()),
                mode,
                new ClientOfferPage.Party(fullName(shipping.getName(), shipping.getSurname()),
                        shipping.getCompanyName(), null, shipping.getStreetAndNumber(), shipping.getPostalCode(),
                        shipping.getCity(), shipping.getCountry(), shipping.getEmail(), shipping.getPhone()));
    }

    private static String fullName(String name, String surname) {
        return Stream.of(name, surname)
                .map(v -> trimToNull(v))
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + " " + b)
                .orElse(null);
    }

    private static boolean sameAddress(ShippingDetails a, ShippingDetails b) {
        return same(a.getName(), b.getName())
                && same(a.getSurname(), b.getSurname())
                && same(a.getCompanyName(), b.getCompanyName())
                && same(a.getStreetAndNumber(), b.getStreetAndNumber())
                && same(a.getPostalCode(), b.getPostalCode())
                && same(a.getCity(), b.getCity())
                && same(a.getCountry(), b.getCountry())
                && same(a.getEmail(), b.getEmail())
                && same(a.getPhone(), b.getPhone());
    }

    private static boolean same(String a, String b) {
        return Objects.equals(trimToNull(a), trimToNull(b));
    }

    private ClientOfferPage.Payment toPayment(Basket basket, Store store, boolean submitted) {
        List<PaymentOptionView> options = store.getPayments().stream().map(this::toPaymentOptionView).toList();
        BillingDetails billing = basket.getBillingDetails();
        boolean splitPayment = billing != null && SplitPaymentPolicy.isRequired(
                billing.toBillingParty(),
                basket.getTotalPrice(),
                store.getInvoicingConfiguration() != null && store.getInvoicingConfiguration().isSplitPaymentsEnabled());
        return new ClientOfferPage.Payment(submitted, splitPayment, options, resolveDefaultPaymentOption(options),
                toBank(basket, store.getDefaultBankAccount()), billing == null ? null : billing.getEmail());
    }

    private ClientOfferPage.Bank toBank(Basket basket, BankAccount account) {
        if (account == null) {
            return null;
        }
        String currency = isBlank(account.getCurrency()) || "PLN".equalsIgnoreCase(account.getCurrency())
                ? null : account.getCurrency();
        return new ClientOfferPage.Bank(account.getAccountHolder(), Iban.grouped(account.getIban()),
                Iban.normalize(account.getIban()), basket.getBasketId(), account.getBankName(),
                account.getSwiftCode(), currency);
    }

    private String resolveDefaultPaymentOption(List<PaymentOptionView> paymentOptions) {
        return paymentOptions.stream()
                .filter(PaymentOptionView::isDefault)
                .map(PaymentOptionView::name)
                .findFirst()
                .orElseGet(() -> paymentOptions.isEmpty() ? null : paymentOptions.get(0).name());
    }

    private PaymentOptionView toPaymentOptionView(PaymentIntegration integration) {
        PaymentProviderDescriptor descriptor = paymentProviderFactory.getDescriptor(integration.getName());
        String displayName = descriptor != null ? descriptor.displayName() : integration.getName();
        return new PaymentOptionView(integration.getName(), displayName, integration.is_default());
    }

    private static String shortId(String id) {
        return id == null || id.length() <= SHORT_ID_LENGTH ? id : id.substring(0, SHORT_ID_LENGTH);
    }
}
