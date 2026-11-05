package pl.commercelink.taxonomy;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

class TaxonomyCodeIndex {

    private final ConcurrentHashMap<String, List<CodeEntry>> entriesByMfn = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> brandKeys = new ConcurrentHashMap<>();

    void link(String mfn, String brand, String ean) {
        if (StringUtils.isAnyBlank(mfn, brand)) return;
        CodeEntry entry = new CodeEntry(brandKeys.computeIfAbsent(brandKey(brand), key -> key), ean);
        entriesByMfn.compute(mfn, (key, entries) -> {
            if (entries == null) return List.of(entry);
            if (entries.contains(entry)) return entries;
            List<CodeEntry> extended = new ArrayList<>(entries);
            extended.add(entry);
            return List.copyOf(extended);
        });
    }

    List<String> eansOf(String mfn, String brand) {
        if (StringUtils.isBlank(brand)) return List.of();
        String key = brandKey(brand);
        return entriesUnder(mfn).stream()
                .filter(entry -> entry.brand().equals(key))
                .map(CodeEntry::ean)
                .toList();
    }

    @Deprecated
    List<String> eansOfSingleBrand(String mfn) {
        List<CodeEntry> entries = entriesUnder(mfn);
        if (entries.stream().map(CodeEntry::brand).distinct().count() != 1) return List.of();
        return entries.stream().map(CodeEntry::ean).distinct().toList();
    }

    void clear() {
        entriesByMfn.clear();
    }

    private List<CodeEntry> entriesUnder(String mfn) {
        if (StringUtils.isBlank(mfn)) return List.of();
        return entriesByMfn.getOrDefault(mfn, List.of());
    }

    private static String brandKey(String brand) {
        return brand.trim().toLowerCase(Locale.ROOT);
    }

    private record CodeEntry(String brand, String ean) {
    }
}
