package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.rma.RMAShippingService;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;

import java.util.List;

/**
 * A package template summarised for the list on the shipping page. {@code parcels} is one line per parcel
 * ("40 × 30 × 20 cm · 5 kg"). A template is incomplete when it has no parcel or one the shipment form would drop
 * (a zero dimension or weight, or no description: {@code ParcelForm.isComplete}). A template whose name starts with
 * {@code RMA - } is offered to customers returning goods ({@code RMAShippingService.RETURN_TEMPLATE_PREFIX}).
 */
public record PackageTemplateView(String id, String name, boolean isDefault, boolean forReturns, List<String> parcels,
                                  boolean complete, String editHref, String defaultHref, String deleteHref) {

    public static PackageTemplateView of(PackageTemplate template, String templatesPath) {
        List<Parcel> parcels = template.getParcels() == null ? List.of() : template.getParcels();
        String base = templatesPath + "/" + template.getId();
        return new PackageTemplateView(template.getId(), template.getName(), template.isDefault(),
                StringUtils.startsWith(template.getName(), RMAShippingService.RETURN_TEMPLATE_PREFIX),
                parcels.stream().map(PackageTemplateView::summary).toList(),
                !parcels.isEmpty() && parcels.stream().allMatch(PackageTemplateView::complete),
                base, base + "/default", base + "/delete");
    }

    private static String summary(Parcel parcel) {
        return parcel.getWidth() + " × " + parcel.getDepth() + " × " + parcel.getHeight() + " cm · " + parcel.getWeight() + " kg"
                + (StringUtils.isNotBlank(parcel.getDescription()) ? " · " + parcel.getDescription().trim() : "");
    }

    private static boolean complete(Parcel parcel) {
        return parcel.getWidth() > 0 && parcel.getDepth() > 0 && parcel.getHeight() > 0 && parcel.getWeight() > 0
                && StringUtils.isNotBlank(parcel.getDescription());
    }
}
