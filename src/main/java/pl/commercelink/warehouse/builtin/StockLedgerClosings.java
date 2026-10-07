package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The stock ledger of every closed month, kept as the report generated when the month was closed: a month is closed
 * while its file exists, and the BZ columns of the file are the opening balance of the months after it.
 */
@Repository
class StockLedgerClosings {

    private static final String FILE_SUFFIX = ".csv";

    private final FileStorage fileStorage;
    private final String bucketName;

    StockLedgerClosings(FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    List<YearMonth> closedMonths(String storeId) {
        String prefix = prefix(storeId);
        return fileStorage.findAllKeysByKeyOrder(bucketName, prefix).stream()
                .map(key -> monthOf(key.substring(prefix.length())))
                .flatMap(Optional::stream)
                .sorted()
                .toList();
    }

    Optional<byte[]> find(String storeId, YearMonth month) {
        String key = key(storeId, month);
        return fileStorage.canRead(bucketName, key) ? Optional.of(fileStorage.getBytes(bucketName, key)) : Optional.empty();
    }

    void save(String storeId, YearMonth month, byte[] report) {
        fileStorage.put(bucketName, key(storeId, month), report);
    }

    Map<String, ClosingBalance> closingBalances(String storeId, YearMonth month) {
        byte[] report = find(storeId, month)
                .orElseThrow(() -> new IllegalStateException("No stock ledger of " + month + " for store " + storeId));
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

    private static Optional<YearMonth> monthOf(String fileName) {
        if (!fileName.endsWith(FILE_SUFFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.parse(fileName.substring(0, fileName.length() - FILE_SUFFIX.length())));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String key(String storeId, YearMonth month) {
        return prefix(storeId) + month + FILE_SUFFIX;
    }

    private static String prefix(String storeId) {
        return storeId + "/stock-ledger/";
    }

    record ClosingBalance(String name, int qty, double value) {
    }
}
