package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * A package template: its name and the parcels an order is sent in. Numbers arrive as text and are checked here, so an
 * empty or wrong value gets a message at the field instead of a binding error page. A parcel whose fields are all empty
 * is skipped (an unused row of a page without JavaScript); any other incomplete parcel is an error, never dropped. The
 * insurance value is not edited: shipping always replaces it with the order's value
 * ({@code ShippingService.retrieveParcelsListBasedOnPackageTemplate}). The description is required because the
 * shipment form drops a parcel without one ({@code ParcelForm.isComplete}).
 */
@Getter
@Setter
public class PackageTemplateForm {

    public static final int MAX_CENTIMETRES = 999;
    public static final int MAX_KILOGRAMS = 999;

    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,4}");

    private String name;
    private boolean makeDefault;
    private List<ParcelRow> parcels = new ArrayList<>();

    public static PackageTemplateForm empty() {
        PackageTemplateForm form = new PackageTemplateForm();
        form.parcels.add(new ParcelRow());
        return form;
    }

    public static PackageTemplateForm from(PackageTemplate template) {
        PackageTemplateForm form = new PackageTemplateForm();
        form.name = template.getName();
        form.makeDefault = template.isDefault();
        template.getParcels().forEach(parcel -> form.parcels.add(ParcelRow.of(parcel)));
        if (form.parcels.isEmpty()) {
            form.parcels.add(new ParcelRow());
        }
        return form;
    }

    public static String fieldId(int index, String field) {
        return "parcel-" + index + "-" + field;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "name", name, "store.shipping.template.name.required");
        boolean anyParcel = false;
        for (int i = 0; i < parcels.size(); i++) {
            ParcelRow parcel = parcels.get(i);
            if (parcel.blank()) {
                continue;
            }
            anyParcel = true;
            requireNumber(errors, fieldId(i, "width"), parcel.width, MAX_CENTIMETRES);
            requireNumber(errors, fieldId(i, "depth"), parcel.depth, MAX_CENTIMETRES);
            requireNumber(errors, fieldId(i, "height"), parcel.height, MAX_CENTIMETRES);
            requireNumber(errors, fieldId(i, "weight"), parcel.weight, MAX_KILOGRAMS);
            FormRules.requireText(errors, fieldId(i, "description"), parcel.description, "store.shipping.template.description.required");
        }
        if (!anyParcel) {
            // The list as a whole, not "Parcel 1 · Width": the problem is that no parcel is filled in.
            errors.put("parcels", "store.shipping.template.parcels.required");
        }
        return errors;
    }

    /** Labels for the error summary: every parcel field is named with its parcel's number. */
    public Map<String, String> errorLabels(BiFunction<Integer, String, String> label) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < parcels.size(); i++) {
            for (String field : List.of("width", "depth", "height", "weight", "description")) {
                labels.put(fieldId(i, field), label.apply(i + 1, field));
            }
        }
        return labels;
    }

    public PackageTemplate toNewTemplate() {
        PackageTemplate template = new PackageTemplate();
        applyTo(template);
        return template;
    }

    /**
     * Edits the template in place; its id and default flag stay as they were. The insured value has no field (the
     * shipment always takes the order's value), but it is carried over by position and a new parcel gets 1: the
     * previous release drops a parcel with value 0 on its next save, so a rollback must not lose parcels.
     */
    public void applyTo(PackageTemplate template) {
        List<Parcel> previous = template.getParcels() == null ? List.of() : template.getParcels();
        template.setName(StringUtils.trim(name));
        List<Parcel> saved = new ArrayList<>();
        for (ParcelRow row : parcels) {
            if (!row.blank()) {
                int index = saved.size();
                int value = index < previous.size() && previous.get(index).getValue() > 0 ? previous.get(index).getValue() : 1;
                saved.add(new Parcel(number(row.width), number(row.depth), number(row.height), number(row.weight), value,
                        StringUtils.trim(row.description)));
            }
        }
        template.setParcels(saved);
    }

    private static void requireNumber(Map<String, String> errors, String id, String value, int max) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            errors.put(id, "store.shipping.template.number.required");
        } else if (!WHOLE_NUMBER.matcher(trimmed).matches() || Integer.parseInt(trimmed) < 1 || Integer.parseInt(trimmed) > max) {
            errors.put(id, "store.shipping.template.number.invalid");
        }
    }

    private static int number(String value) {
        return Integer.parseInt(value.trim());
    }

    @Getter
    @Setter
    public static class ParcelRow {
        private String width;
        private String depth;
        private String height;
        private String weight;
        private String description;

        static ParcelRow of(Parcel parcel) {
            ParcelRow row = new ParcelRow();
            row.width = text(parcel.getWidth());
            row.depth = text(parcel.getDepth());
            row.height = text(parcel.getHeight());
            row.weight = text(parcel.getWeight());
            row.description = parcel.getDescription();
            return row;
        }

        boolean blank() {
            return StringUtils.isAllBlank(width, depth, height, weight, description);
        }

        private static String text(int value) {
            return value > 0 ? String.valueOf(value) : null;
        }
    }
}
