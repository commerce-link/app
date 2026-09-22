package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The "Basics" page of a catalog category: name, order, PIM categories, type, order limit, flags and labels. */
@Getter
@Setter
public class CategoryBasicsForm {

    private String name;
    private String sequenceNumber;
    private List<String> pimCategoryIds = new ArrayList<>();
    private String type = CategoryDefinitionType.Managed.name();
    private String maxQty = "1";
    private boolean requiredDuringOrder;
    private boolean includedInDeliverySuggestions;
    // An unticked checkbox is absent from the POST, so a bound form must read as "protection off" unless the box came back.
    private boolean deletionProtection;
    private List<String> labels = new ArrayList<>();

    /** The form offered for a new category: protected, manual, one piece per order — the defaults of a saved category. */
    public static CategoryBasicsForm forNewCategory() {
        CategoryBasicsForm form = new CategoryBasicsForm();
        form.deletionProtection = true;
        return form;
    }

    public static CategoryBasicsForm from(CategoryDefinition category) {
        CategoryBasicsForm form = new CategoryBasicsForm();
        form.name = category.getName();
        form.sequenceNumber = String.valueOf(category.getSequenceNumber());
        form.pimCategoryIds = new ArrayList<>(category.getPimCategoryIds());
        form.type = category.getType().name();
        form.maxQty = String.valueOf(category.getMaxQty());
        form.requiredDuringOrder = category.isRequiredDuringOrder();
        form.includedInDeliverySuggestions = category.isIncludedInDeliverySuggestions();
        form.deletionProtection = category.isDeletionProtection();
        form.labels = new ArrayList<>(category.getGroupingOrder());
        return form;
    }

    /**
     * @param otherNames  names of the other categories of the catalog (the edited one excluded), for the uniqueness check
     * @param currentName the name the edited category carries today, null when creating. A category that already shares
     *                    its name with another one (saved before the check existed) may be saved as it is; only taking
     *                    over another category's name is refused.
     */
    public Map<String, String> validate(Set<String> otherNames, String currentName) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (FormRules.requireText(errors, "name", name, "catalog.category.name.required")
                && !StringUtils.trimToEmpty(currentName).equalsIgnoreCase(name.trim())
                && otherNames.stream().anyMatch(other -> other != null && other.trim().equalsIgnoreCase(name.trim()))) {
            errors.put("name", "catalog.category.name.duplicate");
        }
        if (StringUtils.isNotBlank(sequenceNumber)) {
            Optional<Integer> sequence = FormNumbers.integer(sequenceNumber);
            if (sequence.isEmpty() || sequence.get() < 1 || sequence.get() > 999) {
                errors.put("sequenceNumber", "catalog.category.sequence.invalid");
            }
        }
        Optional<Integer> max = FormNumbers.integer(maxQty);
        if (max.isEmpty() || max.get() < 1 || max.get() > 999) {
            errors.put("maxQty", "catalog.category.maxQty.invalid");
        }
        if (parseType().isEmpty()) {
            errors.put("type", "catalog.category.type.invalid");
        }
        List<String> cleanLabels = cleanLabels();
        if (cleanLabels.stream().map(String::toLowerCase).distinct().count() != cleanLabels.size()) {
            errors.put("labels", "catalog.category.labels.duplicate");
        }
        return errors;
    }

    public CategoryDefinitions.Basics toBasics() {
        return new CategoryDefinitions.Basics(name.trim(), FormNumbers.integer(sequenceNumber).orElse(0), pimCategoryIds,
                parseType().orElseThrow(), FormNumbers.integer(maxQty).orElse(1), requiredDuringOrder,
                includedInDeliverySuggestions, deletionProtection, cleanLabels());
    }

    public boolean isDynamic() {
        return CategoryDefinitionType.Dynamic.name().equals(type);
    }

    private Optional<CategoryDefinitionType> parseType() {
        try {
            return Optional.of(CategoryDefinitionType.valueOf(StringUtils.defaultString(type)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private List<String> cleanLabels() {
        return labels == null ? List.of() : labels.stream().map(StringUtils::trim).filter(StringUtils::isNotEmpty).toList();
    }
}
