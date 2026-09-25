package pl.commercelink.web.dtos;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.StockDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The "Pricing" page of a category: stock thresholds, availability and the price groups. The "Default" group is required
 * (products without another assignment land there and ProductPricingStrategy throws without it) and always listed first.
 * The others keep the order the category stores them in, which is the order ProductRecommendation matches them in.
 * <p>
 * One group may be several rows (NEW-1, as production "Monitory" uses it): the same name with another rule — label and
 * price threshold. Pricing takes the parameters of the first row of a name (CategoryDefinition.findPriceDefinition), so
 * every row of a name must carry the same parameters, and no two rows of a name the same rule.
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
        /** Put back on the form by a refused removal (RF-19); never bound from a request. */
        @Setter(AccessLevel.NONE)
        private boolean inUse;

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

        /**
         * A price threshold on its own never assigns anything: PriceDefinition.matches refuses a blank labelMatch
         * before it looks at the price. The legend says so with a warning pill instead of reading like a working rule.
         */
        public boolean isAutoSummaryInactive() {
            return StringUtils.isBlank(labelMatch) && StringUtils.isNotBlank(priceMatch);
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
            // Lossless like the multiplier and the markup (N2, F8): FormNumbers.format rounds to two decimals, which
            // would silently change the threshold on a save that never touched the field.
            form.priceMatch = definition.getPriceMatch() > 0 ? FormNumbers.formatDecimalField(definition.getPriceMatch()) : "";
            return form;
        }

        String groupKey() {
            return CategoryPricingForm.groupKey(name);
        }

        /** What picks the row: its label and price threshold as PriceDefinition.matches compares them. */
        Optional<List<Object>> rule() {
            Optional<Double> threshold = StringUtils.isBlank(priceMatch) ? Optional.of(0d) : FormNumbers.decimal(priceMatch);
            return threshold.map(value -> List.<Object>of(StringUtils.trimToEmpty(labelMatch).toLowerCase(Locale.ROOT), value));
        }

        /** The price parameters, by field, in the order of the page; empty when one of them is mistyped. */
        Optional<Map<String, Number>> parameters() {
            Optional<Double> parsedMultiplier = FormNumbers.decimal(multiplier);
            List<Optional<Integer>> amounts = List.of(FormNumbers.integer(minProfit), FormNumbers.integer(critical),
                    FormNumbers.integer(low), FormNumbers.integer(medium));
            if (parsedMultiplier.isEmpty() || amounts.stream().anyMatch(Optional::isEmpty)) {
                return Optional.empty();
            }
            Map<String, Number> parameters = new LinkedHashMap<>();
            parameters.put("multiplier", parsedMultiplier.get());
            parameters.put("minProfit", amounts.get(0).get());
            parameters.put("critical", amounts.get(1).get());
            parameters.put("low", amounts.get(2).get());
            parameters.put("medium", amounts.get(3).get());
            return Optional.of(parameters);
        }

        /** @param groupName the name the group is stored under, the spelling of its first row */
        PriceDefinition toDefinition(String groupName) {
            PriceDefinition definition = new PriceDefinition(FormNumbers.decimal(multiplier).orElse(0d),
                    FormNumbers.integer(minProfit).orElse(0), FormNumbers.integer(critical).orElse(0),
                    FormNumbers.integer(low).orElse(0), FormNumbers.integer(medium).orElse(0),
                    isDefault() ? PriceDefinition.DEFAULT_PRICING_GROUP : groupName);
            definition.setLabelMatch(StringUtils.trimToNull(labelMatch));
            definition.setPriceMatch(FormNumbers.decimal(priceMatch).orElse(0d));
            return definition;
        }
    }

    /** The error of a row whose parameters differ from the first row of its group: "{0}" the group, "{1}" and "{2}" rows. */
    public static final String PARAMETERS_DIFFER = "catalog.category.pricing.group.parameters";

    private String critical;
    private String low;
    private String high;
    private String minQty;
    private String minProviders;
    private List<PriceGroupForm> groups = new ArrayList<>();
    /** Names of the groups of the saved category that the posted form no longer carries; filled in by the controller. */
    private List<String> removedGroups = new ArrayList<>();
    /** The removed group validate() found still used by products; filled in by validate(), never bound from a request. */
    @Setter(AccessLevel.NONE)
    private String groupInUse;
    /** The arguments of the error messages that carry some, by field id; filled in by validate(). */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, Object[]> errorArguments = new HashMap<>();

    /**
     * The name that makes rows one group, the same wherever groups are compared (validation, removal, the in-use check):
     * pricing looks a group up ignoring case, and a stray or doubled space is no new group.
     */
    public static String groupKey(String name) {
        return StringUtils.normalizeSpace(StringUtils.defaultString(name)).toLowerCase(Locale.ROOT);
    }

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

        errorArguments.clear();
        Map<String, Integer> firstRowOfGroup = new HashMap<>();
        Set<List<Object>> rules = new HashSet<>();
        for (int index = 0; index < groups.size(); index++) {
            PriceGroupForm group = groups.get(index);
            if (FormRules.requireText(errors, fieldId(index, "name"), group.name, "catalog.category.pricing.group.name.required")) {
                Integer first = firstRowOfGroup.putIfAbsent(group.groupKey(), index);
                Optional<List<Object>> rule = group.rule();
                if (rule.isPresent() && !rules.add(List.<Object>of(group.groupKey(), rule.get()))) {
                    errors.put(fieldId(index, "name"), "catalog.category.pricing.group.duplicate");
                } else if (first != null) {
                    sameParametersAsTheGroup(errors, index, group, first);
                }
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
                groupInUse = removed.trim();
                break;
            }
        }
        return errors;
    }

    /**
     * A row repeating a group must price like its first row, which is the one pricing uses; the error stands at the
     * first parameter that differs. A mistyped parameter is left to its own message.
     */
    private void sameParametersAsTheGroup(Map<String, String> errors, int index, PriceGroupForm group, int first) {
        Optional<Map<String, Number>> own = group.parameters();
        Optional<Map<String, Number>> ofGroup = groups.get(first).parameters();
        if (own.isEmpty() || ofGroup.isEmpty()) {
            return;
        }
        own.get().keySet().stream()
                .filter(field -> Double.compare(own.get().get(field).doubleValue(), ofGroup.get().get(field).doubleValue()) != 0)
                .findFirst()
                .ifPresent(field -> {
                    errors.put(fieldId(index, field), PARAMETERS_DIFFER);
                    // Strings, not numbers: MessageFormat would group the digits of a number by locale ("1 000").
                    errorArguments.put(fieldId(index, field), new Object[]{StringUtils.normalizeSpace(group.name),
                            String.valueOf(index + 1), String.valueOf(first + 1)});
                });
    }

    /** The arguments of the message of the error at the field; none for a message without placeholders. */
    public Object[] errorArguments(String field) {
        return errorArguments.getOrDefault(field, new Object[0]);
    }

    /** The arguments of every error message that carries some, by field id, for the error summary. */
    public Map<String, Object[]> errorArgumentsByField() {
        return Collections.unmodifiableMap(errorArguments);
    }

    /** The row shares its group with another row: the page marks it as one more rule of that group. */
    public boolean repeatsAGroup(PriceGroupForm group) {
        return StringUtils.isNotBlank(group.name)
                && groups.stream().filter(other -> other.groupKey().equals(group.groupKey())).count() > 1;
    }

    /**
     * RF-19: a removal refused because products use the group puts the group back, every stored row of it, marked as
     * in use, so the form shows what "keep it" keeps. At the end, so the errors of the other rows keep their numbers.
     */
    public void restoreGroupInUse(List<PriceDefinition> saved) {
        if (groupInUse == null) {
            return;
        }
        groups = new ArrayList<>(groups);
        saved.stream().filter(definition -> definition.getPricingGroup() != null
                        && groupKey(definition.getPricingGroup()).equals(groupKey(groupInUse)))
                .map(PriceGroupForm::from)
                .forEach(group -> {
                    group.inUse = true;
                    groups.add(group);
                });
    }

    /** The section CategoryDefinitions applies to the category: the groups in the order of the form. */
    public CategoryDefinitions.Pricing toPricing() {
        return new CategoryDefinitions.Pricing(toStock(), toAvailability(), toGroups());
    }

    public StockDefinition toStock() {
        return new StockDefinition(FormNumbers.integer(critical).orElse(1), FormNumbers.integer(low).orElse(10),
                FormNumbers.integer(high).orElse(30));
    }

    public AvailabilityDefinition toAvailability() {
        return new AvailabilityDefinition(FormNumbers.integer(minQty).orElse(3), FormNumbers.integer(minProviders).orElse(1));
    }

    /**
     * The groups in the order of the form. The rows of one group are stored under the spelling of its first row, so
     * every list of group names (the product page, the in-use check) sees one group once.
     */
    public List<PriceDefinition> toGroups() {
        Map<String, String> spelling = new HashMap<>();
        return groups.stream()
                .map(group -> group.toDefinition(spelling.computeIfAbsent(group.groupKey(), key -> StringUtils.trim(group.name))))
                .toList();
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
