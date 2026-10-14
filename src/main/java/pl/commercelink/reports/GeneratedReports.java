package pl.commercelink.reports;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The files of reports generated from the queue. A file existing is the whole state: there is no record of a report
 * still being generated or of one that failed. The prefix has no store segment in front, so one S3 lifecycle rule
 * expires the files of every store.
 */
@Repository
public class GeneratedReports {

    private static final String ROOT = "generated-reports/";
    private static final String DATE_SEPARATOR = "_";
    private static final String FILE_SUFFIX = ".csv";
    private static final int LISTED = 20;

    private final FileStorage fileStorage;
    private final String bucketName;

    GeneratedReports(FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    public record Entry(ReportType type, LocalDate dateFrom, LocalDate dateTo, Instant generatedAt) {
    }

    void save(String storeId, ReportType type, LocalDate dateFrom, LocalDate dateTo, byte[] file) {
        fileStorage.put(bucketName, key(storeId, type, dateFrom, dateTo), file);
    }

    public Optional<byte[]> find(String storeId, ReportType type, LocalDate dateFrom, LocalDate dateTo) {
        String key = key(storeId, type, dateFrom, dateTo);
        return fileStorage.canRead(bucketName, key) ? Optional.of(fileStorage.getBytes(bucketName, key)) : Optional.empty();
    }

    /** The newest files of the store first. */
    public List<Entry> recent(String storeId) {
        return Stream.of(ReportType.values())
                .filter(type -> type.folder().isPresent())
                .flatMap(type -> entries(storeId, type))
                .sorted(Comparator.comparing(Entry::generatedAt).reversed())
                .limit(LISTED)
                .toList();
    }

    private Stream<Entry> entries(String storeId, ReportType type) {
        String prefix = prefix(storeId, type);
        return fileStorage.findAllKeysByKeyOrder(bucketName, prefix).stream()
                .flatMap(key -> entry(storeId, type, key.substring(prefix.length())).stream());
    }

    private Optional<Entry> entry(String storeId, ReportType type, String fileName) {
        if (!fileName.endsWith(FILE_SUFFIX)) {
            return Optional.empty();
        }
        String[] days = fileName.substring(0, fileName.length() - FILE_SUFFIX.length()).split(DATE_SEPARATOR, -1);
        if (days.length != 2) {
            return Optional.empty();
        }
        try {
            LocalDate from = LocalDate.parse(days[0]);
            LocalDate to = LocalDate.parse(days[1]);
            return fileStorage.findLastModified(bucketName, key(storeId, type, from, to))
                    .map(modified -> new Entry(type, from, to, modified));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String key(String storeId, ReportType type, LocalDate dateFrom, LocalDate dateTo) {
        return prefix(storeId, type) + dateFrom + DATE_SEPARATOR + dateTo + FILE_SUFFIX;
    }

    private static String prefix(String storeId, ReportType type) {
        return ROOT + storeId + "/" + type.folder().orElseThrow(() -> new IllegalArgumentException(type + " keeps no file")) + "/";
    }
}
