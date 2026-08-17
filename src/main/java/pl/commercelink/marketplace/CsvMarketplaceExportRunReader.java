package pl.commercelink.marketplace;

import org.springframework.data.util.Pair;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class CsvMarketplaceExportRunReader implements MarketplaceExportRunReader {

    private static final int COLUMNS_WITH_REMOVAL_ATTEMPTS = 4;
    private static final int LEGACY_COLUMNS = 3;

    @Override
    public boolean handlesFileFormatOf(String key) {
        return key.endsWith(".csv");
    }

    @Override
    public MarketplaceExportRunDocument parse(String key, byte[] fileContent) {
        InputStreamReader reader =
                new InputStreamReader(new ByteArrayInputStream(fileContent), StandardCharsets.UTF_8);
        Pair<String[], List<String[]>> headersAndRows =
                new CSVLoader(reader).readHeadersAndRows(CSVLoader.DEFAULT_SEPARATOR);

        List<MarketplaceOfferSnapshot> offers = new ArrayList<>();
        for (String[] row : headersAndRows.getSecond()) {
            offers.add(toSnapshot(row));
        }
        return MarketplaceExportRunDocument.fromCsvOffers(MarketplaceExportRunReader.runIdFrom(key), offers);
    }

    private MarketplaceOfferSnapshot toSnapshot(String[] csvRow) {
        if (csvRow.length == COLUMNS_WITH_REMOVAL_ATTEMPTS) {
            return new MarketplaceOfferSnapshot(
                    csvRow[0],
                    Long.parseLong(csvRow[1]),
                    Long.parseLong(csvRow[2]),
                    Integer.parseInt(csvRow[3]),
                    false,
                    null);
        }
        if (csvRow.length == LEGACY_COLUMNS) {
            return new MarketplaceOfferSnapshot(
                    csvRow[0],
                    Long.parseLong(csvRow[1]),
                    Long.parseLong(csvRow[2]),
                    0,
                    false,
                    null);
        }
        throw new IllegalArgumentException(
                "Invalid CSV row: expected 3 (legacy) or 4 columns, got " + csvRow.length);
    }
}
