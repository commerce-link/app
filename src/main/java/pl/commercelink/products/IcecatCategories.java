package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategories;
import pl.commercelink.pim.api.PimCategory;

import java.text.Collator;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class IcecatCategories {

    private static final Collator POLISH_COLLATOR = Collator.getInstance(Locale.forLanguageTag("pl-PL"));

    private final PimCatalog pimCatalog;

    public List<String> topLevelNames() {
        return categories().topLevels().stream()
                .map(PimCategory::namePl)
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    public List<String> leafNamesUnder(Collection<String> topLevelNames) {
        PimCategories categories = categories();
        return categories.topLevels().stream()
                .filter(top -> topLevelNames.contains(top.namePl()))
                .flatMap(top -> categories.leavesUnder(top.id()).stream())
                .map(PimCategory::namePl)
                .distinct()
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    private PimCategories categories() {
        return new PimCategories(pimCatalog.allCategories());
    }
}
