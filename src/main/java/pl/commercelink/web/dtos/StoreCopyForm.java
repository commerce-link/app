package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;

import java.util.LinkedHashMap;
import java.util.Map;

/** The super admin's "Copy store" form; newStoreId works as in StoreCreateForm. */
@Getter
@Setter
public class StoreCopyForm {

    private String newStoreName;
    private String newStoreId;

    public static StoreCopyForm of(String suggestedName, String newStoreId) {
        StoreCopyForm form = new StoreCopyForm();
        form.newStoreName = suggestedName;
        form.newStoreId = newStoreId;
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "newStoreName", newStoreName, "store.copy.error.name.required");
        return errors;
    }

    public String trimmedName() {
        return StringUtils.strip(newStoreName);
    }

    public boolean hasWellFormedId() {
        return UniqueIdentifierGenerator.isWellFormed(newStoreId);
    }
}
