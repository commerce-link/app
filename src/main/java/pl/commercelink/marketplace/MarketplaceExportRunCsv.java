package pl.commercelink.marketplace;

import org.springframework.data.util.Pair;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class MarketplaceExportRunCsv {

    private static final String[] HEADERS =
            {"pimId", "price", "quantity", "removalAttempts", "outcome", "reasonCode", "message"};
    private static final String FAILED_SUFFIX = "-failed";
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final String TRUNCATION_MARKER = "\u2026";
    private static final int FULL_COLUMNS = 7;
    private static final int COLUMNS_WITH_REMOVAL_ATTEMPTS = 4;
    private static final int LEGACY_COLUMNS = 3;

    private MarketplaceExportRunCsv() {
    }

    static byte[] toBytes(List<MarketplaceOfferSnapshot> rows) {
        try {
            return new CSVWriter().writeAllRowsToBytes(rows.stream().map(CsvRow::new).toList(), HEADERS);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static List<MarketplaceOfferSnapshot> parse(byte[] content) {
        InputStreamReader reader =
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
        Pair<String[], List<String[]>> headersAndRows =
                new CSVLoader(reader).readHeadersAndRows(CSVLoader.DEFAULT_SEPARATOR);

        List<MarketplaceOfferSnapshot> snapshots = new ArrayList<>();
        for (String[] row : headersAndRows.getSecond()) {
            if (row.length >= LEGACY_COLUMNS) {
                snapshots.add(toSnapshot(row));
            }
        }
        return snapshots;
    }

    static String runIdFrom(String key) {
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        int extension = fileName.lastIndexOf('.');
        String withoutExtension = extension < 0 ? fileName : fileName.substring(0, extension);
        return withoutExtension.endsWith(FAILED_SUFFIX)
                ? withoutExtension.substring(0, withoutExtension.length() - FAILED_SUFFIX.length())
                : withoutExtension;
    }

    static String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String collapsed = message.replaceAll("\\s+", " ").trim();
        return collapsed.length() > MAX_MESSAGE_LENGTH
                ? collapsed.substring(0, MAX_MESSAGE_LENGTH) + TRUNCATION_MARKER
                : collapsed;
    }

    private static MarketplaceOfferSnapshot toSnapshot(String[] row) {
        return new MarketplaceOfferSnapshot(
                row[0],
                Long.parseLong(row[1]),
                Long.parseLong(row[2]),
                row.length >= COLUMNS_WITH_REMOVAL_ATTEMPTS ? Integer.parseInt(row[3]) : 0,
                row.length >= FULL_COLUMNS ? row[4] : "",
                row.length >= FULL_COLUMNS ? row[5] : "",
                row.length >= FULL_COLUMNS ? row[6] : "");
    }

    private record CsvRow(MarketplaceOfferSnapshot snapshot) implements CSVReady {

        @Override
        public String[] asStringArray() {
            return new String[]{
                    snapshot.pimId(),
                    String.valueOf(snapshot.price()),
                    String.valueOf(snapshot.quantity()),
                    String.valueOf(snapshot.removalAttempts()),
                    snapshot.outcome(),
                    snapshot.reasonCode(),
                    normalizeMessage(snapshot.message())
            };
        }
    }
}
