package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A label printer. Settings are posted per printer type as settings[type.key], so a form showing the fields of every
 * type (no JavaScript) still saves only the fields of the chosen type. Stored secrets never go back to the browser:
 * an empty secret field on edit keeps the stored value.
 */
@Getter
@Setter
public class WarehousePrinterForm {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+([.,]\\d+)?");

    private String name;
    private String type;
    private Map<String, String> settings = new HashMap<>();

    public static WarehousePrinterForm empty(String type) {
        WarehousePrinterForm form = new WarehousePrinterForm();
        form.type = type;
        return form;
    }

    public static WarehousePrinterForm from(Printer printer, List<ProviderField> fields) {
        WarehousePrinterForm form = new WarehousePrinterForm();
        form.name = printer.getName();
        form.type = printer.getProviderName();
        if (fields != null) {
            fields.stream()
                    .filter(field -> field.type() != FieldType.PASSWORD)
                    .filter(field -> printer.getSettings().get(field.key()) != null)
                    .forEach(field -> form.settings.put(settingKey(form.type, field), printer.getSettings().get(field.key())));
        }
        return form;
    }

    /** Id of the input of a setting, also the key of its error. */
    public static String fieldId(String type, ProviderField field) {
        return "setting-" + type + "-" + field.key();
    }

    /** Name the input of a setting is posted under. */
    public static String fieldName(String type, ProviderField field) {
        return "settings[" + settingKey(type, field) + "]";
    }

    public String value(String forType, ProviderField field) {
        return field.type() == FieldType.PASSWORD ? null : settings.get(settingKey(forType, field));
    }

    /**
     * @param fields    settings of the chosen type, or null when the type is unknown
     * @param existing  the printer being edited, or null for a new one
     */
    public Map<String, String> validate(List<ProviderField> fields, WarehouseConfiguration configuration, Printer existing) {
        // The summary lists the errors in the order of the fields on the page: the type, then the name, then the settings.
        Map<String, String> errors = new LinkedHashMap<>();
        if (fields == null) {
            errors.put("type", "store.warehouse.printer.type.required");
        }
        if (FormRules.requireText(errors, "name", name, "store.warehouse.printer.name.required")
                && configuration.isPrinterNameTaken(name, existing != null ? existing.getId() : null)) {
            errors.put("name", "store.warehouse.printer.name.taken");
        }
        if (fields == null) {
            return errors;
        }
        for (ProviderField field : fields) {
            String value = StringUtils.trimToNull(settings.get(settingKey(type, field)));
            String id = fieldId(type, field);
            if (value == null) {
                if (field.required() && !hasStoredSecret(existing, field)) {
                    errors.put(id, "store.warehouse.printer.setting.required");
                }
            } else if (field.type() == FieldType.NUMBER && !NUMBER.matcher(value).matches()) {
                errors.put(id, "store.warehouse.printer.setting.number");
            } else if (field.type() == FieldType.URL && !value.startsWith("https://") && !value.startsWith("http://")) {
                errors.put(id, "store.warehouse.printer.setting.url");
            }
        }
        return errors;
    }

    /** Labels of the settings, for the error summary: adapter field labels are plain text, not message keys. */
    public Map<String, String> errorLabels(List<ProviderField> fields) {
        Map<String, String> labels = new HashMap<>();
        if (fields != null) {
            fields.forEach(field -> labels.put(fieldId(type, field), field.label()));
        }
        return labels;
    }

    public void applyTo(Printer printer, List<ProviderField> fields) {
        Map<String, String> saved = new HashMap<>();
        for (ProviderField field : fields) {
            String value = StringUtils.trimToNull(settings.get(settingKey(type, field)));
            if (value != null) {
                saved.put(field.key(), value);
            } else if (hasStoredSecret(printer, field)) {
                saved.put(field.key(), printer.getSettings().get(field.key()));
            }
        }
        printer.setName(StringUtils.trimToNull(name));
        printer.setProviderName(type);
        printer.setSettings(saved);
    }

    private boolean hasStoredSecret(Printer existing, ProviderField field) {
        return existing != null
                && field.type() == FieldType.PASSWORD
                && type != null && type.equals(existing.getProviderName())
                && existing.getSettings() != null
                && StringUtils.isNotBlank(existing.getSettings().get(field.key()));
    }

    private static String settingKey(String type, ProviderField field) {
        return type + "." + field.key();
    }
}
