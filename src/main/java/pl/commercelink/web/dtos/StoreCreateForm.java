package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.starter.security.tenant.DefaultApiKeyValidator;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;

import java.util.LinkedHashMap;
import java.util.Map;

/** The super admin's "New store" form. */
@Getter
@Setter
public class StoreCreateForm {

    private String name;
    private String apiKey;
    /**
     * The id the new store will get, handed out with the page and posted back in a hidden field: the same form sent twice
     * names one store, which the second POST finds instead of creating another (as CatalogSettingsForm.newCatalogId).
     */
    private String newStoreId;

    public static StoreCreateForm withId(String newStoreId) {
        StoreCreateForm form = new StoreCreateForm();
        form.newStoreId = newStoreId;
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "name", name, "store.create.error.name.required");
        String key = StringUtils.strip(apiKey);
        if (StringUtils.isNotEmpty(key) && key.length() < DefaultApiKeyValidator.KEY_SUFFIX_LENGTH) {
            errors.put("apiKey", "store.create.error.apiKey.short");
        } else if (StringUtils.isNotEmpty(key) && !isHeaderSafe(key)) {
            errors.put("apiKey", "store.create.error.apiKey.chars");
        }
        return errors;
    }

    // The suffix is compared with the end of the x-api-key header, which carries printable ASCII only: a key with a space
    // or a Polish letter would be saved and never match, so the store's REST API would refuse every request.
    private static boolean isHeaderSafe(String key) {
        return key.chars().allMatch(c -> c > 0x20 && c < 0x7F);
    }

    public String trimmedName() {
        return StringUtils.strip(name);
    }

    /** What DefaultApiKeyValidator compares with the end of the x-api-key header, so a pasted whole key still works. */
    public String apiKeySuffix() {
        String key = StringUtils.stripToNull(apiKey);
        return key == null ? null : StringUtils.right(key, DefaultApiKeyValidator.KEY_SUFFIX_LENGTH);
    }

    public boolean hasWellFormedId() {
        return UniqueIdentifierGenerator.isWellFormed(newStoreId);
    }
}
