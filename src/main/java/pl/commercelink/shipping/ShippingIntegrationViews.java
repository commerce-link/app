package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds what the shipping page shows about the integrations: the "Wyślij przez" card and the Allegro form. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingIntegrationViews {

    static final Set<String> LABEL_FORMATS = Set.of("PDF_A6", "PDF_A4", "ZPL");
    static final String DEFAULT_LABEL_FORMAT = "PDF_A6";

    private final MessageSource messageSource;
    private final ShippingProviderFactory shippingProviderFactory;

    public ShippingIntegrationChoiceView choice(List<ShippingIntegrationOption> options, String selected, Locale locale) {
        ShippingIntegrationOption allegro = ShippingIntegrationChoice.availableNamed(options, ShippingIntegrationChoice.ALLEGRO)
                .orElse(null);
        String help = allegro != null && allegro.suggested()
                ? messageSource.getMessage("shipping.integration.suggested.allegro", null, locale) : null;
        String warning = null;
        if (allegro != null && selected != null && !selected.equals(ShippingIntegrationChoice.ALLEGRO)) {
            String other = options.stream().filter(o -> o.name().equals(selected))
                    .map(ShippingIntegrationOption::displayName).findFirst().orElse(selected);
            warning = messageSource.getMessage("shipping.integration.warning.allegroOrder",
                    new Object[]{allegro.proposal().methodName(), other}, locale);
        }
        return new ShippingIntegrationChoiceView(options, selected, help, warning);
    }

    public AllegroShippingView allegro(ShipmentProposal proposal, Order order, Store store, Locale locale) {
        String point = proposal.deliveryPoint() == null ? null : StringUtils.trimToNull(proposal.deliveryPoint().code());
        String deliveryTypeKey = proposal.deliveryType() == null ? null
                : "shipping.allegro.deliveryType." + proposal.deliveryType().name();
        return new AllegroShippingView(proposal.methodName(), AllegroCarrierNames.displayName(proposal.carrierId()),
                point, deliveryTypeKey, recipient(order.getShippingDetails()), limits(proposal, locale),
                codHelp(order, proposal, locale), insuranceHelp(proposal, locale),
                "shipping.allegro.labelFormat." + labelFormat(store), "/dashboard/store/shipping/allegro", codAccount(store));
    }

    /** The default bank account ShippingService sends with the cash on delivery, as "holder, IBAN"; null without one. */
    private static String codAccount(Store store) {
        BankAccount account = store.getDefaultBankAccount();
        if (account == null) {
            return null;
        }
        return Stream.of(account.getAccountHolder(), account.getIban()).filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }

    /** The reasons of the Allegro form, by field, in the operator's language; several on one field are joined. */
    public Map<String, String> errors(List<AllegroShipmentFormCheck.Problem> problems, Locale locale) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (AllegroShipmentFormCheck.Problem problem : problems) {
            String text = messageSource.getMessage(problem.key(), problem.args(), locale);
            errors.merge(problem.field(), text, (a, b) -> a + " " + b);
        }
        return errors;
    }

    private String limits(ShipmentProposal proposal, Locale locale) {
        PackageOption option = AllegroShipmentFormCheck.packageOption(proposal);
        List<String> parts = new ArrayList<>();
        if (option != null && option.maxLength() != null && option.maxWidth() != null && option.maxHeight() != null) {
            parts.add(messageSource.getMessage("shipping.allegro.limits.dimensions", new Object[]{
                    AllegroShipmentFormCheck.plain(option.maxLength()), AllegroShipmentFormCheck.plain(option.maxWidth()),
                    AllegroShipmentFormCheck.plain(option.maxHeight())}, locale));
        }
        if (option != null && option.maxWeight() != null) {
            parts.add(messageSource.getMessage("shipping.allegro.limits.weight",
                    new Object[]{AllegroShipmentFormCheck.plain(option.maxWeight())}, locale));
        }
        return parts.isEmpty()
                ? messageSource.getMessage("shipping.allegro.limits.none", new Object[]{proposal.methodName()}, locale)
                : messageSource.getMessage("shipping.allegro.limits", new Object[]{proposal.methodName(),
                        String.join(", ", parts)}, locale);
    }

    private String codHelp(Order order, ShipmentProposal proposal, Locale locale) {
        String due = amount(BigDecimal.valueOf(order.getUnpaidAmount()), locale);
        return proposal.maxCashOnDelivery() == null
                ? messageSource.getMessage("shipping.allegro.cod.help", new Object[]{due}, locale)
                : messageSource.getMessage("shipping.allegro.cod.help.max",
                        new Object[]{due, amount(proposal.maxCashOnDelivery(), locale)}, locale);
    }

    private String insuranceHelp(ShipmentProposal proposal, Locale locale) {
        return proposal.maxInsurance() == null
                ? messageSource.getMessage("shipping.allegro.insurance.help", null, locale)
                : messageSource.getMessage("shipping.allegro.insurance.help.max",
                        new Object[]{amount(proposal.maxInsurance(), locale)}, locale);
    }

    /** The format chosen in the Wysyłam z Allegro settings; the default when the settings cannot be read. */
    String labelFormat(Store store) {
        try {
            String value = shippingProviderFactory.loadConfiguration(store, ShippingIntegrationChoice.ALLEGRO)
                    .get("labelFormat");
            return LABEL_FORMATS.contains(value) ? value : DEFAULT_LABEL_FORMAT;
        } catch (RuntimeException e) {
            log.warn("Wysyłam z Allegro settings of store {} could not be read", store.getStoreId(), e);
            return DEFAULT_LABEL_FORMAT;
        }
    }

    private static String recipient(ShippingDetails details) {
        if (details == null) {
            return null;
        }
        String name = StringUtils.trimToEmpty(details.getFullName());
        String phone = StringUtils.trimToNull(details.getPhone());
        return phone == null ? name : name + " · " + phone;
    }

    private static String amount(BigDecimal value, Locale locale) {
        return String.format(locale, "%,.2f", value);
    }
}
