package pl.commercelink.taxonomy;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.brand.NoNameBrand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class TaxonomyCodeIndex {

    private final ConcurrentHashMap<String, List<CodeEntry>> entriesByMfn = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<CodeEntry>> blockedByMfn = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> brandKeys = new ConcurrentHashMap<>();

    void link(String mfn, String brand, String ean) {
        if (StringUtils.isAnyBlank(mfn, brand) || NoNameBrand.is(brand)) return;
        CodeEntry entry = entry(brand, ean);
        entriesByMfn.compute(mfn, (key, entries) -> {
            if (entries == null) return List.of(entry);
            if (entries.contains(entry)) return entries;
            List<CodeEntry> extended = new ArrayList<>(entries);
            extended.add(entry);
            return List.copyOf(extended);
        });
    }

    void block(String mfn, String brand, String ean) {
        if (StringUtils.isAnyBlank(mfn, brand)) return;
        blockedByMfn.computeIfAbsent(mfn, key -> ConcurrentHashMap.newKeySet()).add(entry(brand, ean));
    }

    List<String> eansOf(String mfn, String brand) {
        if (StringUtils.isBlank(brand)) return List.of();
        String key = brandKey(brand);
        Set<CodeEntry> blocked = blockedUnder(mfn);
        return entriesUnder(mfn).stream()
                .filter(entry -> entry.brand().equals(key))
                .filter(entry -> !blocked.contains(entry))
                .map(CodeEntry::ean)
                .toList();
    }

    void clear() {
        entriesByMfn.clear();
        blockedByMfn.clear();
    }

    private CodeEntry entry(String brand, String ean) {
        return new CodeEntry(brandKeys.computeIfAbsent(brandKey(brand), key -> key), ean);
    }

    private List<CodeEntry> entriesUnder(String mfn) {
        if (StringUtils.isBlank(mfn)) return List.of();
        return entriesByMfn.getOrDefault(mfn, List.of());
    }

    private Set<CodeEntry> blockedUnder(String mfn) {
        if (StringUtils.isBlank(mfn)) return Set.of();
        return blockedByMfn.getOrDefault(mfn, Set.of());
    }

    private static String brandKey(String brand) {
        return brand.trim().toLowerCase(Locale.ROOT);
    }

    private record CodeEntry(String brand, String ean) {
    }
}
