package pl.commercelink.taxonomy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
@Slf4j
@DependsOn("initializingBeanRunner")
public class TaxonomyCatalog {

    private final TaxonomyCatalogRepository repository;

    public TaxonomyCatalog(TaxonomyCatalogRepository repository) {
        this.repository = repository;
    }

    public Taxonomy findByMfn(String mfn) {
        return isBlank(mfn) ? null : repository.find(mfn);
    }

    public Map<String, Taxonomy> findByMfns(Collection<String> mfns) {
        List<String> wanted = mfns.stream().filter(mfn -> !isBlank(mfn)).distinct().toList();
        return wanted.isEmpty() ? Map.of() : repository.findAll(wanted);
    }

    public Taxonomy findBest(Collection<String> productCodes) {
        return Taxonomy.bestOf(productCodes, findByMfns(productCodes));
    }

    public TaxonomyMerge openMerge(Collection<String> mfns) {
        return new TaxonomyMerge(findByMfns(mfns));
    }

    public void commit(TaxonomyMerge merge) {
        List<Taxonomy> keptCategory = new ArrayList<>();
        for (Taxonomy merged : merge.changed()) {
            Taxonomy seen = merge.stored(merged.mfn());
            if (Taxonomy.hasCategory(seen)) {
                keptCategory.add(merged);
            } else {
                writeGuardingAgainstAConcurrentCategory(merged, seen);
            }
        }
        repository.saveAll(keptCategory);
    }

    private void writeGuardingAgainstAConcurrentCategory(Taxonomy merged, Taxonomy seen) {
        if (repository.saveIfCategoryUnchanged(merged, seen)) {
            return;
        }
        Taxonomy fresh = repository.find(merged.mfn());
        if (!repository.saveIfCategoryUnchanged(mergeOf(fresh, merged), fresh)) {
            log.warn("Taxonomy record kept changing while a feed chunk was in flight, left to the next import: mfn={}",
                    merged.mfn());
        }
    }

    public boolean updateCategory(String mfn, String category, String categoryId) {
        if (isBlank(mfn) || isBlank(category) || isBlank(categoryId)) {
            return false;
        }
        return repository.updateCategoryIfAbsent(mfn, category, categoryId);
    }

    public void forEachCategorized(Consumer<Taxonomy> consumer) {
        repository.forEachCategorized(consumer);
    }

    public long approximateSize() {
        return repository.approximateSize();
    }

    static Taxonomy mergeOf(Taxonomy current, Taxonomy incoming) {
        if (current == null) return incoming;

        Taxonomy winner = bestByCategoryThenScore(current, incoming);
        Integer net = bestWeightOf(current, incoming, Taxonomy::netWeightInGrams);
        Integer gross = bestWeightOf(current, incoming, Taxonomy::grossWeightInGrams);

        return needsRebuild(winner, net, gross) ? withWeights(winner, net, gross) : winner;
    }

    private static Taxonomy bestByCategoryThenScore(Taxonomy current, Taxonomy incoming) {
        if (Taxonomy.hasCategory(current) != Taxonomy.hasCategory(incoming)) {
            return Taxonomy.hasCategory(current) ? current : incoming;
        }
        return bestByScore(current, incoming);
    }

    private static Taxonomy bestByScore(Taxonomy a, Taxonomy b) {
        return b.dataAccuracyScore() <= a.dataAccuracyScore() ? b : a;
    }

    private static Integer bestWeightOf(Taxonomy a, Taxonomy b, Function<Taxonomy, Integer> picker) {
        return Stream.of(b, a)
                .filter(t -> picker.apply(t) != null)
                .min(Comparator.comparingInt(Taxonomy::dataAccuracyScore))
                .map(picker)
                .orElse(null);
    }

    private static boolean needsRebuild(Taxonomy winner, Integer net, Integer gross) {
        return !Objects.equals(net, winner.netWeightInGrams())
                || !Objects.equals(gross, winner.grossWeightInGrams());
    }

    private static Taxonomy withWeights(Taxonomy t, Integer net, Integer gross) {
        return new Taxonomy(t.ean(), t.mfn(), t.brand(), t.name(),
                            t.category(), t.dataAccuracyScore(), net, gross, t.rawCategory(), t.categoryId());
    }
}
