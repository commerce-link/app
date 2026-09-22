package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.StockDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The "Pricing" page of a category: stock thresholds, availability and the price groups. The "Default" group is required
 * (products without another assignment land there and ProductPricingStrategy throws without it) and always listed first.
 * The others keep the order the category stores them in, which is the order ProductRecommendation matches them in.
 */
@Getter
@Setter
public class CategoryPricingForm {

    @Getter
    @Setter
    public static class PriceGroupForm {

        private String name;
        private String multiplier = "1,00";
        private String minProfit = "0";
        private String critical = "0";
        private String low = "0";
        private String medium = "0";
        private String labelMatch;
        private String priceMatch;

        public boolean isDefault() {
            return PriceDefinition.DEFAULT_PRICING_GROUP.equalsIgnoreCase(StringUtils.trim(name));
        }

        /**
         * The message the legend says what the group picks up by itself with, or null when nothing is set. One key per
         * combination rather than a built sentence: both halves are translated, and so is what joins them.
         */
        public String autoSummaryKey() {
            boolean label = StringUtils.isNotBlank(labelMatch);
            boolean price = StringUtils.isNotBlank(priceMatch);
            if (label && price) {
                return "catalog.category.pricing.auto.summary.both";
            }
            if (label) {
                return "catalog.category.pricing.auto.summary.label";
            }
            return price ? "catalog.category.pricing.auto.summary.price" : null;
        }

        static PriceGroupForm from(PriceDefinition definition) {
            PriceGroupForm form = new PriceGroupForm();
            form.name = definition.getPricingGroup();
            form.multiplier = FormNumbers.formatDecimalField(definition.getMultiplier());
            form.minProfit = String.valueOf(definition.getMinProfit());
            form.critical = String.valueOf(definition.getCriticalStockPriceAdjustment());
            form.low = String.valueOf(definition.getLowStockPriceAdjustment());
            form.medium = String.valueOf(definition.getMediumStockPriceAdjustment());
            form.labelMatch = definition.getLabelMatch();
            form.priceMatch = definition.getPriceMatch() > 0 ? FormNumbers.format(definition.getPriceMatch()) : "";
            return form;
        }

        PriceDefinition toDefinition() {
            PriceDefinition definition = new PriceDefinition(FormNumbers.decimal(multiplier).orElse(0d),
                    FormNumbers.integer(minProfit).orElse(0), FormNumbers.integer(critical).orElse(0),
                    FormNumbers.integer(low).orElse(0), FormNumbers.integer(medium).orElse(0),
                    isDefault() ? PriceDefinition.DEFAULT_PRICING_GROUP : StringUtils.trim(name));
            definition.setLabelMatch(StringUtils.trimToNull(labelMatch));
            definition.setPriceMatch(FormNumbers.decimal(priceMatch).orElse(0d));
            return definition;
        }
    }

    private String critical;
    private String low;
    private String high;
    private String minQty;
    private String minProviders;
    private List<PriceGroupForm> groups = new ArrayList<>();
    /** Names of the groups of the saved category that the posted form no longer carries; filled in by the controller. */
    private List<String> removedGroups = new ArrayList<>();

    public static CategoryPricingForm from(CategoryDefinition category) {
        CategoryPricingForm form = new CategoryPricingForm();
        StockDefinition stock = category.getStockDefinition() == null ? new StockDefinition(1, 10, 30) : category.getStockDefinition();
        AvailabilityDefinition availability = category.getAvailabilityDefinition() == null
                ? new AvailabilityDefinition(3, 1) : category.getAvailabilityDefinition();
        form.critical = String.valueOf(stock.getCriticalStockThreshold());
        form.low = String.valueOf(stock.getLowStockThreshold());
        form.high = String.valueOf(stock.getHighStockThreshold());
        form.minQty = String.valueOf(availability.getTotalMinQty());
        form.minProviders = String.valueOf(availability.getMinNumberOfProviders());
        // Only "Default" is moved: re-sorting the rest would hide which group a product is picked up by first.
        form.groups = category.getPriceDefinitions().stream()
                .map(PriceGroupForm::from)
                .sorted(Comparator.comparing((PriceGroupForm group) -> !group.isDefault()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (form.groups.stream().noneMatch(PriceGroupForm::isDefault)) {
            PriceGroupForm missing = new PriceGroupForm();
            missing.name = PriceDefinition.DEFAULT_PRICING_GROUP;
            form.groups.add(0, missing);
        }
        return form;
    }

    /** The id of a field of one price group: also its error key, so the error summary can link to the field. */
    public static String fieldId(int index, String field) {
        return "group-" + index + "-" + field;
    }

    /** @param productsInGroup how many products of the category are in a group; asked only about the removed ones */
    public Map<String, String> validate(Function<String, Integer> productsInGroup) {
        Map<String, String> errors = new LinkedHashMap<>();
        Optional<Integer> criticalThreshold = atLeastOne(errors, "critical", critical, "catalog.category.pricing.threshold.invalid");
        Optional<Integer> lowThreshold = atLeastOne(errors, "low", low, "catalog.category.pricing.threshold.invalid");
        Optional<Integer> highThreshold = atLeastOne(errors, "high", high, "catalog.category.pricing.threshold.invalid");
        // A threshold out of order is named by the ordering rule rather than by the range it also breaks.
        if (criticalThreshold.isPresent() && lowThreshold.isPresent() && lowThreshold.get() < criticalThreshold.get()) {
            errors.put("low", "catalog.category.pricing.low.order");
        }
        if (lowThreshold.isPresent() && highThreshold.isPresent() && highThreshold.get() < lowThreshold.get()) {
            errors.put("high", "catalog.category.pricing.high.order");
        }
        atLeastOne(errors, "minQty", minQty, "catalog.category.pricing.availability.invalid");
        atLeastOne(errors, "minProviders", minProviders, "catalog.category.pricing.availability.invalid");

        Set<String> seen = new HashSet<>();
        for (int index = 0; index < groups.size(); index++) {
            PriceGroupForm group = groups.get(index);
            if (FormRules.requireText(errors, fieldId(index, "name"), group.name, "catalog.category.pricing.group.name.required")
                    && !seen.add(group.name.trim().toLowerCase())) {
                errors.put(fieldId(index, "name"), "catalog.category.pricing.group.duplicate");
            }
            if (FormNumbers.decimal(group.multiplier).filter(value -> value > 0).isEmpty()) {
                errors.put(fieldId(index, "multiplier"), "catalog.category.pricing.multiplier.invalid");
            }
            nonNegative(errors, fieldId(index, "minProfit"), group.minProfit);
            nonNegative(errors, fieldId(index, "critical"), group.critical);
            nonNegative(errors, fieldId(index, "low"), group.low);
            nonNegative(errors, fieldId(index, "medium"), group.medium);
            if (StringUtils.isNotBlank(group.priceMatch) && FormNumbers.decimal(group.priceMatch).filter(value -> value >= 0).isEmpty()) {
                errors.put(fieldId(index, "priceMatch"), "catalog.category.pricing.priceMatch.invalid");
            }
        }
        if (groups.stream().noneMatch(PriceGroupForm::isDefault)) {
            errors.put("groups", "catalog.category.pricing.default.required");
        }
        for (String removed : removedGroups) {
            // Each question is a full read of the products table, so the first group still in use ends the search.
            if (StringUtils.isNotBlank(removed) && productsInGroup.apply(removed.trim()) > 0) {
                errors.put("groups", "catalog.category.pricing.group.inUse");
                break;
            }
        }
        return errors;
    }

    public StockDefinition toStock() {
        return new StockDefinition(FormNumbers.integer(critical).orElse(1), FormNumbers.integer(low).orElse(10),
                FormNumbers.integer(high).orElse(30));
    }

    public AvailabilityDefinition toAvailability() {
        return new AvailabilityDefinition(FormNumbers.integer(minQty).orElse(3), FormNumbers.integer(minProviders).orElse(1));
    }

    public List<PriceDefinition> toGroups() {
        return groups.stream().map(PriceGroupForm::toDefinition).toList();
    }

    /** Returns the number as typed, out of range or not, so a rule that compares two fields can still name the real problem. */
    private static Optional<Integer> atLeastOne(Map<String, String> errors, String field, String value, String messageKey) {
        Optional<Integer> parsed = FormNumbers.integer(value);
        if (parsed.filter(number -> number >= 1).isEmpty()) {
            errors.put(field, messageKey);
        }
        return parsed;
    }

    private static void nonNegative(Map<String, String> errors, String field, String value) {
        if (FormNumbers.integer(value).filter(number -> number >= 0).isEmpty()) {
            errors.put(field, "catalog.category.pricing.amount.invalid");
        }
    }
}
