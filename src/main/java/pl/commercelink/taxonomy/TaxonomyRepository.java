package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
public class TaxonomyRepository {

    @Autowired
    private FileStorage fileStorage;

    @Value("${s3.bucket.datalake}")
    private String bucketName;

    public Pair<String, List<Taxonomy>> loadNewest() {
        try {
            Pair<String, InputStreamReader> newest = fileStorage.findNewest(bucketName, "taxonomy/");
            if (newest == null) {
                return Pair.of("N/A", new ArrayList<>());
            }

            String fileName = newest.getLeft();
            CSVLoader csvLoader = new CSVLoader(newest.getRight());

            List<Taxonomy> taxonomies = new ArrayList<>();
            csvLoader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
                try {
                    taxonomies.add(TaxonomyParser.fromCsvRow(row));
                } catch (Exception e) {
                    System.err.println("Failed to load taxonomy row: " + e.getMessage());
                }
            });
            return Pair.of(fileName, taxonomies);

        } catch (Exception e) {
            System.err.println("Failed to load taxonomies: " + e.getMessage());
        }

        return  Pair.of("N/A", new ArrayList<>());
    }

    public void save(Collection<Taxonomy> taxonomies) {
        byte[] csvBytes = TaxonomyParser.toCsv(taxonomies);
        String fileName = "taxonomy/" + LocalDate.now() + ".csv";
        fileStorage.put(bucketName, fileName, csvBytes);
    }
}
