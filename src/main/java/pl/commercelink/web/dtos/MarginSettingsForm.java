package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.MarginConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Settings › Margins: the default low-margin percent and the percents of chosen categories. Percents arrive as text
 * ("10", "12,5", "12.5 %") and are checked here, so a wrong value gets a message at its field. A row with neither a
 * category nor a percent is skipped (an unused row of a page without JavaScript); a half-filled row is an error, never
 * dropped. A category may appear once, compared ignoring case and spaces, the way order items are matched
 * ({@link MarginConfiguration#thresholdFor}).
 */
@Getter
@Setter
public class MarginSettingsForm {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private String defaultPercent;
    private List<CategoryRow> categories = new ArrayList<>();

    public static MarginSettingsForm from(MarginConfiguration configuration) {
        MarginSettingsForm form = new MarginSettingsForm();
        if (configuration != null) {
            form.defaultPercent = text(configuration.getDefaultPercent());
            if (configuration.getCategories() != null) {
                configuration.getCategories().forEach(margin -> form.categories.add(
                        new CategoryRow(margin.getCategory(), text(margin.getPercent()))));
            }
        }
        if (form.categories.isEmpty()) {
            form.categories.add(new CategoryRow());
        }
        return form;
    }

    public static String fieldId(int index, String field) {
        return "category-" + index + "-" + field;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(defaultPercent) && parse(defaultPercent) == null) {
            errors.put("defaultPercent", "store.margins.percent.invalid");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < categories.size(); i++) {
            CategoryRow row = categories.get(i);
            if (row.blank()) {
                continue;
            }
            String category = StringUtils.trimToNull(row.category);
            if (category == null) {
                errors.put(fieldId(i, "category"), "store.margins.category.required");
            } else if (!seen.add(category.toLowerCase(Locale.ROOT))) {
                errors.put(fieldId(i, "category"), "store.margins.category.duplicate");
            }
            if (StringUtils.isBlank(row.percent)) {
                errors.put(fieldId(i, "percent"), "store.margins.percent.required");
            } else if (parse(row.percent) == null) {
                errors.put(fieldId(i, "percent"), "store.margins.percent.invalid");
            }
        }
        return errors;
    }

    /** Labels for the error summary: every row field is named with its row's number. */
    public Map<String, String> errorLabels(IntFunction<String> category, IntFunction<String> percent) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            labels.put(fieldId(i, "category"), category.apply(i + 1));
            labels.put(fieldId(i, "percent"), percent.apply(i + 1));
        }
        return labels;
    }

    /** The configuration to save; call only after {@link #validate()} found nothing. */
    public MarginConfiguration toConfiguration() {
        List<MarginConfiguration.CategoryMargin> margins = new ArrayList<>();
        for (CategoryRow row : categories) {
            if (!row.blank()) {
                margins.add(new MarginConfiguration.CategoryMargin(StringUtils.trim(row.category),
                        parse(row.percent).doubleValue()));
            }
        }
        BigDecimal fallback = StringUtils.isBlank(defaultPercent) ? null : parse(defaultPercent);
        return new MarginConfiguration(fallback == null ? null : fallback.doubleValue(), margins);
    }

    /**
     * A percent above 0 and below 100 with at most two decimals, written with a comma or a dot and an optional "%";
     * null for anything else.
     */
    public static BigDecimal parse(String percent) {
        String text = StringUtils.trimToEmpty(percent).replace("%", "").replace(',', '.').strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.signum() <= 0 || value.compareTo(HUNDRED) >= 0 || value.stripTrailingZeros().scale() > 2) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Double percent) {
        return percent == null ? null : BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    @Getter
    @Setter
    public static class CategoryRow {
        private String category;
        private String percent;

        public CategoryRow() {
        }

        public CategoryRow(String category, String percent) {
            this.category = category;
            this.percent = percent;
        }

        boolean blank() {
            return StringUtils.isAllBlank(category, percent);
        }
    }
}
