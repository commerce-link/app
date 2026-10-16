package pl.commercelink.payments.statements;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.payments.statements.ParsedStatement.Problem;
import pl.commercelink.payments.statements.ParsedStatement.RowProblem;
import pl.commercelink.stores.BankStatementFormat;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a bank's CSV history of operations with the account's {@link BankStatementFormat}. Nothing is stored: the
 * operations live as long as the reconciliation page that shows them.
 */
public final class BankStatementParser {

    private static final Pattern UNNAMED_COLUMN = Pattern.compile("\\[(\\d+)]");

    private BankStatementParser() {
    }

    /** accountKey makes derived operation numbers differ between the store's accounts. */
    public static ParsedStatement parse(byte[] content, BankStatementFormat format, String accountKey) {
        List<Row> data = new ArrayList<>(rows(content, format));
        List<String> headers = headers(data, format);
        if (format.getHeaderRow() > 0) {
            data.remove(0);
        }
        Columns columns = Columns.of(headers, format);
        DateTimeFormatter dates = dateFormatter(format.getDateFormat());

        List<BankOperation> operations = new ArrayList<>();
        List<RowProblem> problems = new ArrayList<>();
        Map<String, Integer> ordinals = new HashMap<>();
        int skipped = 0;
        for (Row row : data) {
            if (row.isBlank()) {
                continue;
            }
            // a row without a date is no operation: a closing balance, a total
            if (StringUtils.isBlank(row.cell(columns.date()))) {
                skipped++;
                continue;
            }
            LocalDate date = date(row.cell(columns.date()), dates);
            BigDecimal amount = amount(row, columns, format.isDecimalComma());
            // words where the date should be and no amount: a summary line; a row with one of them is an operation we
            // failed to read
            if (date == null && amount == null) {
                skipped++;
                continue;
            }
            if (date == null || amount == null) {
                problems.add(new RowProblem(row.line(), date == null ? Problem.DATE : Problem.AMOUNT));
                continue;
            }
            if (amount.signum() == 0) {
                skipped++;
                continue;
            }
            String title = columns.titles().stream().map(row::cell).filter(StringUtils::isNotBlank)
                    .reduce((first, second) -> first + " " + second).map(BankStatementParser::collapse).orElse("");
            String sender = collapse(row.cell(columns.sender()));
            // mBank writes the account as '8310... so a spreadsheet keeps it text
            String senderAccount = row.cell(columns.senderAccount()).replaceAll("[^0-9A-Za-z]", "");
            String number = StringUtils.trimToNull(row.cell(columns.transactionNo()));
            if (number == null) {
                String key = String.join("|", StringUtils.defaultString(accountKey), date.toString(),
                        amount.toPlainString(), title.toLowerCase(Locale.ROOT), senderAccount);
                int ordinal = ordinals.merge(key, 1, Integer::sum);
                number = OperationNumbers.of(key, ordinal);
            }
            operations.add(new BankOperation(row.line(), date, amount, title, sender, senderAccount, number));
        }
        return new ParsedStatement(headers, operations, problems, skipped);
    }

    /** The header cells of a file read with these settings (or "[1]", "[2]"... without a header row). */
    public static List<String> headers(byte[] content, BankStatementFormat format) {
        return headers(rows(content, format), format);
    }

    /** The column name used for a file without a header row. */
    public static String unnamedColumn(int index) {
        return "[" + (index + 1) + "]";
    }

    public static String decode(byte[] content, String encoding) {
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (RuntimeException e) {
            throw new StatementFileException("statement.file.error.encoding", encoding);
        }
        String text = new String(content, charset);
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    public static char separatorChar(String separator) {
        return StringUtils.isEmpty(separator) ? ';' : separator.charAt(0);
    }

    /** The records from the header row on (or from the first line), each with the physical line it starts on. */
    static List<Row> rows(byte[] content, BankStatementFormat format) {
        String[] lines = decode(content, format.getEncoding()).split("\r\n|\r|\n", -1);
        // the preamble before the header (account holder, period, balances) is cut off by lines, not parsed: its quotes
        // and separators need not make sense as CSV
        int skip = Math.max(0, format.getHeaderRow() - 1);
        if (skip >= lines.length) {
            throw new StatementFileException("statement.file.error.noHeader", format.getHeaderRow());
        }
        String rest = String.join("\n", Arrays.copyOfRange(lines, skip, lines.length));
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(separatorChar(format.getSeparator()))
                .withEscapeChar(ICSVParser.NULL_CHARACTER)
                .build();
        List<Row> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(rest)).withCSVParser(parser).build()) {
            long read = 0;
            while (true) {
                String[] cells;
                try {
                    cells = reader.readNext();
                } catch (CsvMalformedLineException e) {
                    read = reader.getLinesRead();
                    continue;
                }
                if (cells == null) {
                    break;
                }
                rows.add(new Row(skip + (int) read + 1, cells));
                read = reader.getLinesRead();
            }
        } catch (IOException | CsvValidationException e) {
            throw new StatementFileException("statement.file.error.unreadable");
        }
        return rows;
    }

    private static List<String> headers(List<Row> rows, BankStatementFormat format) {
        if (format.getHeaderRow() > 0) {
            if (rows.isEmpty() || rows.get(0).isBlank()) {
                throw new StatementFileException("statement.file.error.noHeader", format.getHeaderRow());
            }
            return Arrays.stream(rows.get(0).cells()).map(BankStatementParser::header).toList();
        }
        int width = rows.stream().mapToInt(row -> row.cells().length).max().orElse(0);
        List<String> headers = new ArrayList<>();
        for (int index = 0; index < width; index++) {
            headers.add(unnamedColumn(index));
        }
        return headers;
    }

    private static DateTimeFormatter dateFormatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (RuntimeException e) {
            throw new StatementFileException("statement.file.error.dateFormat", pattern);
        }
    }

    private static LocalDate date(String cell, DateTimeFormatter formatter) {
        String text = cell.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException e) {
            // some banks add the time ("2026-10-07 12:31:05")
            String day = text.split("[\\sT]", 2)[0];
            try {
                return LocalDate.parse(day, formatter);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static BigDecimal amount(Row row, Columns columns, boolean decimalComma) {
        if (columns.amount() >= 0) {
            return StatementAmounts.parse(row.cell(columns.amount()), decimalComma);
        }
        BigDecimal credit = StatementAmounts.parse(row.cell(columns.credit()), decimalComma);
        BigDecimal debit = StatementAmounts.parse(row.cell(columns.debit()), decimalComma);
        if (credit != null && credit.signum() != 0) {
            return credit.abs();
        }
        if (debit != null && debit.signum() != 0) {
            return debit.abs().negate();
        }
        return credit != null ? credit : debit;
    }

    private static String header(String cell) {
        return collapse(cell == null ? "" : cell.replace("\ufeff", ""));
    }

    private static String collapse(String text) {
        return text == null ? "" : text.replaceAll("[\\s\\u00a0]+", " ").trim();
    }

    record Row(int line, String[] cells) {

        String cell(int index) {
            return index >= 0 && index < cells.length && cells[index] != null ? cells[index] : "";
        }

        boolean isBlank() {
            return Arrays.stream(cells).allMatch(StringUtils::isBlank);
        }
    }

    private record Columns(int date, int amount, int credit, int debit, List<Integer> titles, int sender,
                           int senderAccount, int transactionNo) {

        static Columns of(List<String> headers, BankStatementFormat format) {
            boolean split = format.isSplitAmount();
            List<Integer> titles = format.getTitleColumns() == null ? List.of() : format.getTitleColumns().stream()
                    .filter(StringUtils::isNotBlank).map(name -> index(headers, name)).toList();
            return new Columns(index(headers, format.getDateColumn()),
                    split ? -1 : index(headers, format.getAmountColumn()),
                    split ? index(headers, format.getCreditColumn()) : -1,
                    split ? index(headers, format.getDebitColumn()) : -1,
                    titles, optional(headers, format.getSenderColumn()), optional(headers, format.getSenderAccountColumn()),
                    optional(headers, format.getTransactionNoColumn()));
        }

        private static int optional(List<String> headers, String name) {
            return StringUtils.isBlank(name) ? -1 : index(headers, name);
        }

        private static int index(List<String> headers, String name) {
            if (StringUtils.isBlank(name)) {
                throw new StatementFileException("statement.file.error.column", "");
            }
            Matcher unnamed = UNNAMED_COLUMN.matcher(name.trim());
            if (unnamed.matches()) {
                return Integer.parseInt(unnamed.group(1)) - 1;
            }
            String wanted = header(name);
            for (int index = 0; index < headers.size(); index++) {
                if (headers.get(index).equalsIgnoreCase(wanted)) {
                    return index;
                }
            }
            throw new StatementFileException("statement.file.error.column", wanted);
        }
    }
}
