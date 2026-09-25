package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCatalogRepository;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.List;

@ChangeUnit(id = "V018-seed-taxonomy-from-daily-file", order = "018", author = "commercelink")
@Slf4j
public class V018_SeedTaxonomyFromDailyFile {

    private static final int CHUNK_SIZE = 1_000;

    private final TaxonomyRepository taxonomyRepository;
    private final TaxonomyCatalogRepository catalogRepository;

    public V018_SeedTaxonomyFromDailyFile(TaxonomyRepository taxonomyRepository,
                                          TaxonomyCatalogRepository catalogRepository) {
        this.taxonomyRepository = taxonomyRepository;
        this.catalogRepository = catalogRepository;
    }

    @Execution
    public void seed() {
        var newest = taxonomyRepository.loadNewest();
        List<Taxonomy> taxonomies = newest.getRight();
        if (taxonomies.isEmpty()) {
            log.info("No taxonomy file to seed the catalog from, starting empty");
            return;
        }
        for (int from = 0; from < taxonomies.size(); from += CHUNK_SIZE) {
            catalogRepository.saveAll(taxonomies.subList(from, Math.min(from + CHUNK_SIZE, taxonomies.size())));
        }
        log.info("Seeded {} taxonomy items into the catalog from file: {}", taxonomies.size(), newest.getLeft());
    }

    @RollbackExecution
    public void rollback() {}
}
