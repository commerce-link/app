package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategories;
import pl.commercelink.pim.api.PimCategory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PimCategoryOptions {

    private static final Collator POLISH_COLLATOR = Collator.getInstance(Locale.forLanguageTag("pl-PL"));

    private static final String LANG = "pl";

    private final PimCatalog pimCatalog;

    public List<String> topLevelNames() {
        return categories().topLevels().stream()
                .map(PimCategory::name)
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    public List<String> leafNamesUnder(Collection<String> topLevelNames) {
        PimCategories categories = categories();
        return categories.topLevels().stream()
                .filter(top -> topLevelNames.contains(top.name()))
                .flatMap(top -> categories.leavesUnder(top.id()).stream())
                .map(PimCategory::name)
                .distinct()
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    public List<String> categoryOptions(Collection<String> topLevelNames, Collection<String> currentValues) {
        List<String> options = new ArrayList<>(leafNamesUnder(topLevelNames));
        currentValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !options.contains(value))
                .distinct()
                .forEach(options::add);
        options.sort(POLISH_COLLATOR);
        return List.copyOf(options);
    }

    private PimCategories categories() {
        return new PimCategories(pimCatalog.allCategories().stream()
                .filter(category -> LANG.equals(category.lang()))
                .toList());
    }
}
