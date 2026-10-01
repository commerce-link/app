package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings › Order fulfilment: delivery time, how new orders are fulfilled and the customer's order page. The day counts
 * are taken as text so a blank or mistyped value gets a message at the field instead of a binding error. The supplier
 * settings of the same configuration (connections, global configuration, inventory cache) belong to the suppliers page
 * and are carried over from the store unchanged.
 */
@Getter
@Setter
public class FulfilmentSettingsForm {

    public static final int MAX_DAYS = 60;

    private String orderAssemblyDays;
    private String orderRealizationDays;
    private String defaultFulfilmentType;
    private boolean automatedFulfilment;
    private boolean clientOrderPageEnabled;
    private boolean clientShippingAddressChangeEnabled;
    private boolean clientPreferredShippingDateEnabled;
    private Map<String, List<String>> preferredShippingDays = new LinkedHashMap<>();

    public static FulfilmentSettingsForm from(Store store) {
        FulfilmentConfiguration config = store.getFulfilmentConfiguration() != null
                ? store.getFulfilmentConfiguration() : new FulfilmentConfiguration();
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.orderAssemblyDays = String.valueOf(config.getOrderAssemblyDays());
        form.orderRealizationDays = String.valueOf(config.getOrderRealizationDays());
        form.defaultFulfilmentType = config.getDefaultFulfilmentType() != null
                ? config.getDefaultFulfilmentType().name() : FulfilmentType.WarehouseFulfilment.name();
        form.automatedFulfilment = config.isAutomatedFulfilment();
        form.clientOrderPageEnabled = config.isClientOrderPageEnabled();
        form.clientShippingAddressChangeEnabled = config.isClientShippingAddressChangeEnabled();
        form.clientPreferredShippingDateEnabled = config.isClientPreferredShippingDateEnabled();
        for (ShipmentType type : ShipmentType.values()) {
            form.preferredShippingDays.put(type.name(), dayNames(config.preferredShippingDaysFor(type)));
        }
        return form;
    }

    public boolean hasPreferredShippingDay(String type, String day) {
        List<String> days = preferredShippingDays.get(type);
        return days != null && days.contains(day);
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (days(orderAssemblyDays) == null) {
            errors.put("orderAssemblyDays", "store.fulfilment.days.invalid");
        }
        if (days(orderRealizationDays) == null) {
            errors.put("orderRealizationDays", "store.fulfilment.days.invalid");
        }
        if (fulfilmentType() == null) {
            errors.put("defaultFulfilmentType", "store.fulfilment.type.required");
        }
        for (ShipmentType type : ShipmentType.values()) {
            if (selectedDays(type).isEmpty()) {
                errors.put("preferredShippingDays", "store.fulfilment.preferredDays.required");
            }
        }
        return errors;
    }

    /** The configuration to save: this form's fields over a copy of the store's current one. Call after validate(). */
    public FulfilmentConfiguration toFulfilmentConfiguration(Store store) {
        FulfilmentConfiguration current = store.getFulfilmentConfiguration() != null
                ? store.getFulfilmentConfiguration() : new FulfilmentConfiguration();
        FulfilmentConfiguration config = current.withConnections(current.getSupplierConnections());
        config.setOrderAssemblyDays(days(orderAssemblyDays));
        config.setOrderRealizationDays(days(orderRealizationDays));
        config.setDefaultFulfilmentType(fulfilmentType());
        config.setAutomatedFulfilment(automatedFulfilment);
        config.setClientOrderPageEnabled(clientOrderPageEnabled);
        config.setClientShippingAddressChangeEnabled(clientShippingAddressChangeEnabled);
        config.setClientPreferredShippingDateEnabled(clientPreferredShippingDateEnabled);
        Map<String, List<String>> days = new LinkedHashMap<>();
        for (ShipmentType type : ShipmentType.values()) {
            days.put(type.name(), dayNames(selectedDays(type)));
        }
        config.setPreferredShippingDays(days);
        return config;
    }

    private EnumSet<DayOfWeek> selectedDays(ShipmentType type) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        List<String> names = preferredShippingDays.get(type.name());
        if (names == null) {
            return days;
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            if (names.contains(day.name())) {
                days.add(day);
            }
        }
        return days;
    }

    private static List<String> dayNames(Iterable<DayOfWeek> days) {
        EnumSet<DayOfWeek> ordered = EnumSet.noneOf(DayOfWeek.class);
        days.forEach(ordered::add);
        return ordered.stream().map(DayOfWeek::name).toList();
    }

    private FulfilmentType fulfilmentType() {
        for (FulfilmentType type : FulfilmentType.values()) {
            if (type.name().equals(defaultFulfilmentType)) {
                return type;
            }
        }
        return null;
    }

    private static Integer days(String value) {
        String trimmed = StringUtils.trimToEmpty(value);
        if (!trimmed.matches("\\d{1,3}")) {
            return null;
        }
        int days = Integer.parseInt(trimmed);
        return days <= MAX_DAYS ? days : null;
    }
}
