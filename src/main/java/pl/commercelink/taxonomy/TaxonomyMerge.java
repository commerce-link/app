package pl.commercelink.taxonomy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class TaxonomyMerge {

    private final Map<String, Taxonomy> stored;
    private final Map<String, Taxonomy> merged = new LinkedHashMap<>();

    TaxonomyMerge(Map<String, Taxonomy> stored) {
        this.stored = stored;
    }

    public Taxonomy knownFor(String mfn) {
        Taxonomy candidate = merged.get(mfn);
        return candidate != null ? candidate : stored.get(mfn);
    }

    public void apply(Taxonomy candidate) {
        if (candidate == null || isBlank(candidate.mfn())) {
            return;
        }
        merged.put(candidate.mfn(), TaxonomyCatalog.mergeOf(knownFor(candidate.mfn()), candidate));
    }

    Taxonomy stored(String mfn) {
        return stored.get(mfn);
    }

    List<Taxonomy> changed() {
        return merged.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(stored.get(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
    }
}
