package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The provider of a single-instance integration (invoicing today; shipping later) and its adapter settings. Settings are
 * posted per provider as settings[provider.key], so a page showing the fields of every provider (no JavaScript) still
 * saves only those of the chosen one. Stored secrets never go back to the browser: an empty secret field keeps the
 * stored value, which {@code ProviderConfigurationManager} merges in on save.
 */
@Getter
@Setter
public class IntegrationSettingsForm {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+([.,]\\d+)?");

    private String providerName;
    private Map<String, String> settings = new HashMap<>();

    /** @param stored the configuration as {@code loadConfigurationForUI} returns it: secrets present but blanked */
    public static IntegrationSettingsForm from(String providerName, Map<String, String> stored, List<ProviderField> fields) {
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.providerName = providerName;
        if (fields != null && stored != null) {
            fields.stream()
                    .filter(field -> field.type() != FieldType.PASSWORD)
                    .filter(field -> stored.get(field.key()) != null)
                    .forEach(field -> form.settings.put(settingKey(providerName, field), stored.get(field.key())));
        }
        return form;
    }

    /** Id of the input of a setting, also the key of its error. */
    public static String fieldId(String provider, ProviderField field) {
        return "setting-" + provider + "-" + field.key();
    }

    /** Name the input of a setting is posted under. */
    public static String fieldName(String provider, ProviderField field) {
        return "settings[" + settingKey(provider, field) + "]";
    }

    public String value(String forProvider, ProviderField field) {
        return field.type() == FieldType.PASSWORD ? null : settings.get(settingKey(forProvider, field));
    }

    /**
     * @param fields            settings of the chosen provider, or null when it is not installed
     * @param storedSecretKeys  keys of secrets already saved for the chosen provider, which may be left empty
     */
    public Map<String, String> validate(List<ProviderField> fields, Set<String> storedSecretKeys) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (fields == null) {
            errors.put("providerName", "integration.provider.required");
            return errors;
        }
        for (ProviderField field : fields) {
            String value = StringUtils.trimToNull(settings.get(settingKey(providerName, field)));
            String id = fieldId(providerName, field);
            if (value == null) {
                boolean storedSecret = field.type() == FieldType.PASSWORD && storedSecretKeys.contains(field.key());
                if (field.required() && !storedSecret) {
                    errors.put(id, "integration.setting.required");
                }
            } else if (field.type() == FieldType.NUMBER && !NUMBER.matcher(value).matches()) {
                errors.put(id, "integration.setting.number");
            } else if (field.type() == FieldType.URL && !value.startsWith("https://") && !value.startsWith("http://")) {
                errors.put(id, "integration.setting.url");
            }
        }
        return errors;
    }

    /** Labels of the settings, for the error summary: adapter field labels are plain text, not message keys. */
    public Map<String, String> errorLabels(List<ProviderField> fields) {
        Map<String, String> labels = new HashMap<>();
        if (fields != null) {
            fields.forEach(field -> labels.put(fieldId(providerName, field), field.label()));
        }
        return labels;
    }

    /** The configuration to save: an empty secret is left out so the stored one is kept. */
    public Map<String, String> toConfiguration(List<ProviderField> fields) {
        Map<String, String> configuration = new HashMap<>();
        for (ProviderField field : fields) {
            String value = StringUtils.trimToNull(settings.get(settingKey(providerName, field)));
            if (value != null) {
                configuration.put(field.key(), value);
            }
        }
        return configuration;
    }

    private static String settingKey(String provider, ProviderField field) {
        return provider + "." + field.key();
    }
}
