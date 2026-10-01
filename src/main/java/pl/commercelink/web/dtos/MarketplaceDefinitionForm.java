package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.products.MarketplaceDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Export rules of one category for one marketplace. Mirrors MarketplaceDefinition.isComplete(): a positive markup and at
 * least one condition — a warehouse quantity, or a quantity per distributor with a number of (local) distributors.
 */
@Getter
@Setter
public class MarketplaceDefinitionForm {

    // An unticked checkbox is absent from the POST, so a bound form must read as "off" unless the box came back.
    private boolean enabled;
    private String markup = "1,00";
    private String minWarehouseQty = "0";
    private String minQtyPerDistributor = "0";
    private String minNumOfDistributors = "0";
    private String minNumOfLocalDistributors = "0";
    private String minDistributorsQty = "0";
    private boolean exportSelectedProducts;

    /** The form offered for a marketplace with no definition yet: export on, no condition chosen, all products. */
    public static MarketplaceDefinitionForm empty() {
        MarketplaceDefinitionForm form = new MarketplaceDefinitionForm();
        form.enabled = true;
        return form;
    }

    public static MarketplaceDefinitionForm from(MarketplaceDefinition definition) {
        MarketplaceDefinitionForm form = new MarketplaceDefinitionForm();
        form.enabled = definition.isEnabled();
        form.markup = FormNumbers.formatDecimalField(definition.getMarkup());
        form.minWarehouseQty = String.valueOf(definition.getMinWarehouseQty());
        form.minQtyPerDistributor = String.valueOf(definition.getMinQtyPerDistributor());
        form.minNumOfDistributors = String.valueOf(definition.getMinNumOfDistributors());
        form.minNumOfLocalDistributors = String.valueOf(definition.getMinNumOfLocalDistributors());
        form.minDistributorsQty = String.valueOf(definition.getMinDistributorsQty());
        form.exportSelectedProducts = definition.isExportSelectedProducts();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (FormNumbers.decimal(markup).filter(value -> value > 0).isEmpty()) {
            errors.put("markup", "catalog.category.marketplace.markup.invalid");
        }
        int warehouse = threshold(errors, "minWarehouseQty", minWarehouseQty);
        int perDistributor = threshold(errors, "minQtyPerDistributor", minQtyPerDistributor);
        int distributors = threshold(errors, "minNumOfDistributors", minNumOfDistributors);
        int local = threshold(errors, "minNumOfLocalDistributors", minNumOfLocalDistributors);
        threshold(errors, "minDistributorsQty", minDistributorsQty);
        boolean distributorRule = perDistributor > 0 && (distributors > 0 || local > 0);
        if (warehouse <= 0 && !distributorRule) {
            errors.put("conditions", "catalog.category.marketplace.conditions.required");
        }
        return errors;
    }

    public MarketplaceDefinition toDefinition(String name) {
        MarketplaceDefinition definition = new MarketplaceDefinition(name, FormNumbers.decimal(markup).orElse(0d),
                FormNumbers.integer(minDistributorsQty).orElse(0), FormNumbers.integer(minQtyPerDistributor).orElse(0),
                FormNumbers.integer(minNumOfDistributors).orElse(0), FormNumbers.integer(minNumOfLocalDistributors).orElse(0),
                FormNumbers.integer(minWarehouseQty).orElse(0));
        definition.setEnabled(enabled);
        definition.setExportSelectedProducts(exportSelectedProducts);
        return definition;
    }

    /** A mistyped threshold is reported at its own field and counts as "no condition" for the rule below. */
    private static int threshold(Map<String, String> errors, String field, String value) {
        Optional<Integer> parsed = FormNumbers.integer(value).filter(parsedValue -> parsedValue >= 0);
        if (parsed.isEmpty()) {
            errors.put(field, "catalog.category.marketplace.threshold.invalid");
            return -1;
        }
        return parsed.get();
    }
}
