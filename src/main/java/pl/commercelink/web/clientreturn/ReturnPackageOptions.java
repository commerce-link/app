package pl.commercelink.web.clientreturn;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.rma.RMAShippingService;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ReturnPackageOptions {

    private ReturnPackageOptions() {
    }

    /**
     * Same filter and order as RMAShippingService.getAvailableReturnOptions, so both agree on what is offered, except a
     * template without an id: an older record the shipping settings page has not given one yet, which the booking cannot
     * look up.
     */
    public static List<ReturnPackageOption> of(Store store) {
        return store.getPackageTemplates().stream()
                .filter(template -> StringUtils.startsWith(template.getName(), RMAShippingService.RETURN_TEMPLATE_PREFIX))
                .filter(template -> template.getId() != null)
                .map(ReturnPackageOptions::from)
                .collect(Collectors.toList());
    }

    /** Any template of the store: a booked return may point at one that has since lost its prefix. */
    public static Optional<ReturnPackageOption> find(Store store, String templateId) {
        return store.getPackageTemplates().stream()
                .filter(template -> Objects.equals(template.getId(), templateId))
                .findFirst()
                .map(ReturnPackageOptions::from);
    }

    public static String selected(List<ReturnPackageOption> options, String posted) {
        if (options.isEmpty()) {
            return null;
        }
        return options.stream().filter(option -> Objects.equals(option.id(), posted)).findFirst()
                .or(() -> options.stream().filter(ReturnPackageOption::isDefault).findFirst())
                .orElse(options.get(0))
                .id();
    }

    private static ReturnPackageOption from(PackageTemplate template) {
        List<Parcel> parcels = template.getParcels() == null ? List.of() : template.getParcels();
        String description = parcels.isEmpty() ? null : parcels.stream()
                .map(parcel -> parcel.getWidth() + " × " + parcel.getDepth() + " × " + parcel.getHeight()
                        + " cm · " + parcel.getWeight() + " kg")
                .collect(Collectors.joining(" + "));
        return new ReturnPackageOption(template.getId(), title(template.getName()), parcels.size(),
                description, template.isDefault());
    }

    private static String title(String name) {
        String stripped = StringUtils.removeStart(name, RMAShippingService.RETURN_TEMPLATE_PREFIX);
        String trimmed = stripped == null ? "" : stripped.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
