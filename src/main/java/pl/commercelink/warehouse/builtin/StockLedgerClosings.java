package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The stock ledger of every closed period, kept as the report generated when the period was closed
 * ({@code <from>_<to>.csv}): a period is closed while its file exists, and the BZ columns of the file are the opening
 * balance of the reports starting after it.
 */
@Repository
class StockLedgerClosings {

    private static final String FILE_SUFFIX = ".csv";
    private static final String DATE_SEPARATOR = "_";

    private final FileStorage fileStorage;
    private final String bucketName;

    StockLedgerClosings(FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    List<StockLedgerPeriod> closedPeriods(String storeId) {
        String prefix = prefix(storeId);
        return fileStorage.findAllKeysByKeyOrder(bucketName, prefix).stream()
                .map(key -> periodOf(key.substring(prefix.length())))
                .flatMap(Optional::stream)
                .sorted(StockLedgerPeriod.BY_END)
                .toList();
    }

    Optional<byte[]> find(String storeId, StockLedgerPeriod period) {
        String key = key(storeId, period);
        return fileStorage.canRead(bucketName, key) ? Optional.of(fileStorage.getBytes(bucketName, key)) : Optional.empty();
    }

    void save(String storeId, StockLedgerPeriod period, byte[] report) {
        fileStorage.put(bucketName, key(storeId, period), report);
    }

    Map<String, ClosingBalance> closingBalances(String storeId, StockLedgerPeriod period) {
        byte[] report = find(storeId, period)
                .orElseThrow(() -> new IllegalStateException("No stock ledger of " + period.label() + " for store " + storeId));
        return parse(report);
    }

    static Map<String, ClosingBalance> parse(byte[] report) {
        Pair<String[], List<String[]>> table = new CSVLoader(
                new InputStreamReader(new ByteArrayInputStream(report), StandardCharsets.UTF_8))
                .readHeadersAndRows(CSVLoader.DEFAULT_SEPARATOR);
        List<String> headers = Arrays.asList(table.getFirst());
        int mfn = column(headers, StockLedgerRow.MFN_HEADER);
        int name = column(headers, StockLedgerRow.NAME_HEADER);
        int qty = column(headers, StockLedgerRow.CLOSING_QTY_HEADER);
        int value = column(headers, StockLedgerRow.CLOSING_VALUE_HEADER);

        Map<String, ClosingBalance> balances = new HashMap<>();
        for (String[] row : table.getSecond()) {
            balances.put(row[mfn], new ClosingBalance(row[name], Integer.parseInt(row[qty]), StockLedgerRow.parseMoney(row[value])));
        }
        return balances;
    }

    private static int column(List<String> headers, String header) {
        int index = headers.indexOf(header);
        if (index < 0) {
            throw new IllegalStateException("Stock ledger without the column " + header);
        }
        return index;
    }

    private static Optional<StockLedgerPeriod> periodOf(String fileName) {
        if (!fileName.endsWith(FILE_SUFFIX)) {
            return Optional.empty();
        }
        String[] dates = fileName.substring(0, fileName.length() - FILE_SUFFIX.length()).split(DATE_SEPARATOR, -1);
        if (dates.length != 2) {
            return Optional.empty();
        }
        try {
            StockLedgerPeriod period = new StockLedgerPeriod(LocalDate.parse(dates[0]), LocalDate.parse(dates[1]));
            return period.from().isAfter(period.to()) ? Optional.empty() : Optional.of(period);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String key(String storeId, StockLedgerPeriod period) {
        return prefix(storeId) + period.from() + DATE_SEPARATOR + period.to() + FILE_SUFFIX;
    }

    private static String prefix(String storeId) {
        return storeId + "/reports/stock-ledger/";
    }

    record ClosingBalance(String name, int qty, double value) {
    }
}
