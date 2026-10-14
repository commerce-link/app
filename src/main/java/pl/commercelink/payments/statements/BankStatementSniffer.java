package pl.commercelink.payments.statements;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.BankStatementFormat;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A first guess of a bank's CSV format from a sample file, so the settings page opens filled in: the encoding (UTF-8 when
 * the bytes are valid UTF-8, otherwise Windows-1250, which Polish banks use), the separator, the header row below the
 * preamble, the date format, the decimal mark and the columns, recognised by their Polish or English names. The operator
 * checks the preview and corrects whatever was guessed wrong.
 */
public final class BankStatementSniffer {

    public static final String UTF_8 = "UTF-8";
    public static final String WINDOWS_1250 = "windows-1250";

    private static final List<String> SEPARATORS = List.of(";", ",", "\t", "|");
    private static final int SAMPLE_LINES = 200;
    private static final Map<Pattern, String> DATE_FORMATS = new LinkedHashMap<>();
    private static final Pattern AMOUNT = Pattern.compile("^[+\\-\\u2212]?[\\d\\s\\u00a0.,']*\\d([.,]\\d{1,2})?-?(\\s?(PLN|zł|EUR))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMA_DECIMAL = Pattern.compile(",\\d{2}\\s*-?(\\s?(PLN|zł|EUR))?$", Pattern.CASE_INSENSITIVE);

    static {
        DATE_FORMATS.put(Pattern.compile("^\\d{4}-\\d{2}-\\d{2}"), "yyyy-MM-dd");
        DATE_FORMATS.put(Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}"), "dd.MM.yyyy");
        DATE_FORMATS.put(Pattern.compile("^\\d{2}-\\d{2}-\\d{4}"), "dd-MM-yyyy");
        DATE_FORMATS.put(Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}"), "yyyy.MM.dd");
        DATE_FORMATS.put(Pattern.compile("^\\d{2}/\\d{2}/\\d{4}"), "dd/MM/yyyy");
        DATE_FORMATS.put(Pattern.compile("^\\d{4}/\\d{2}/\\d{2}"), "yyyy/MM/dd");
    }

    private BankStatementSniffer() {
    }

    /** The format as far as it can be told from the file; columns not recognised are left blank. */
    public static BankStatementFormat guess(byte[] content) {
        BankStatementFormat format = new BankStatementFormat();
        format.setEncoding(isUtf8(content) ? UTF_8 : WINDOWS_1250);
        String[] lines = BankStatementParser.decode(content, format.getEncoding()).split("\r\n|\r|\n", -1);
        List<String> sample = Arrays.asList(lines).subList(0, Math.min(lines.length, SAMPLE_LINES));
        format.setSeparator(separator(sample));
        format.setDecimalComma(true);
        format.setDateFormat("yyyy-MM-dd");

        char separator = BankStatementParser.separatorChar(format.getSeparator());
        // operations are the lines with the most common number of cells; the preamble's lines (period, balances) are shorter
        int width = modalWidth(sample, separator);
        int firstData = -1;
        String[] dataCells = null;
        for (int index = 0; index < sample.size(); index++) {
            String[] cells = cells(sample.get(index), separator);
            if (cells.length >= width - 1 && dateIndex(cells) >= 0 && hasAmount(cells)) {
                firstData = index;
                dataCells = cells;
                break;
            }
        }
        if (firstData < 0) {
            format.setHeaderRow(1);
            return format;
        }
        int header = -1;
        for (int index = firstData - 1; index >= 0; index--) {
            String[] cells = cells(sample.get(index), separator);
            if (cells.length >= width - 1 && dateIndex(cells) < 0
                    && Arrays.stream(cells).filter(StringUtils::isNotBlank).count() >= 2) {
                header = index;
                break;
            }
        }
        format.setHeaderRow(header + 1);
        String dateCell = dataCells[dateIndex(dataCells)].trim();
        DATE_FORMATS.entrySet().stream()
                .filter(entry -> entry.getKey().matcher(dateCell).find())
                .findFirst()
                .ifPresent(entry -> format.setDateFormat(entry.getValue()));
        List<String> amounts = Arrays.stream(dataCells).map(String::trim)
                .filter(cell -> cell.matches(".*\\d.*") && AMOUNT.matcher(cell).matches())
                .toList();
        boolean commaDecimal = amounts.stream().anyMatch(cell -> COMMA_DECIMAL.matcher(cell).find());
        boolean dotDecimal = amounts.stream().anyMatch(cell -> cell.matches(".*\\.\\d{2}\\s*-?$"));
        format.setDecimalComma(commaDecimal || !dotDecimal);

        List<String> headers = header >= 0
                ? Arrays.stream(cells(sample.get(header), separator)).map(cell -> cell.replace("\ufeff", "").trim()).toList()
                : unnamed(dataCells.length);
        guessColumns(format, headers, dataCells);
        return format;
    }

    private static void guessColumns(BankStatementFormat format, List<String> headers, String[] dataCells) {
        Set<String> used = new HashSet<>();
        format.setDateColumn(first(headers, used, "data ksiegowania", "data operacji", "data transakcji", "data", "date"));
        String amount = first(headers, used, "kwota operacji", "kwota transakcji", "kwota", "amount");
        if (amount != null) {
            format.setAmountColumn(amount);
        } else {
            format.setCreditColumn(first(headers, used, "uznania", "wplywy", "ma", "credit"));
            format.setDebitColumn(first(headers, used, "obciazenia", "wydatki", "winien", "debit"));
        }
        List<String> titles = new ArrayList<>();
        String description = first(headers, used, "opis operacji", "opis transakcji", "szczegoly");
        String title = first(headers, used, "tytul", "title", "opis", "description");
        if (description != null) {
            titles.add(description);
        }
        if (title != null) {
            titles.add(title);
        }
        format.setTitleColumns(titles);
        format.setSenderColumn(first(headers, used, "nadawca", "kontrahent", "nazwa nadawcy", "odbiorca", "nazwa", "sender"));
        // not a bare "rachunek": mBank's operations list has #Rachunek with the store's own account in it
        format.setSenderAccountColumn(first(headers, used, "rachunek nadawcy", "rachunek kontrahenta", "konto nadawcy",
                "konto kontrahenta", "numer konta", "iban kontrahenta"));
        format.setTransactionNoColumn(first(headers, used, "numer referencyjny", "nr referencyjny", "id transakcji",
                "numer transakcji", "numer operacji", "reference"));
        if (headers.stream().allMatch(name -> name.startsWith("[")) && format.getDateColumn() == null) {
            int date = dateIndex(dataCells);
            format.setDateColumn(BankStatementParser.unnamedColumn(date));
        }
    }

    private static String first(List<String> headers, Set<String> used, String... names) {
        for (String name : names) {
            for (String header : headers) {
                String normalized = normalize(header);
                boolean hit = normalized.equals(name) || (name.length() > 3 && normalized.contains(name));
                if (hit && used.add(header)) {
                    return header;
                }
            }
        }
        return null;
    }

    static String normalize(String text) {
        String plain = Normalizer.normalize(text.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plain.replace("#", "").replaceAll("[\\s_]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> unnamed(int width) {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < width; index++) {
            names.add(BankStatementParser.unnamedColumn(index));
        }
        return names;
    }

    private static int dateIndex(String[] cells) {
        for (int index = 0; index < cells.length; index++) {
            String cell = cells[index].trim();
            for (Pattern pattern : DATE_FORMATS.keySet()) {
                if (pattern.matcher(cell).find()) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean hasAmount(String[] cells) {
        for (String raw : cells) {
            String cell = raw.trim();
            if (!cell.isEmpty() && !isDate(cell) && cell.matches(".*\\d.*") && AMOUNT.matcher(cell).matches()
                    && (cell.contains(",") || cell.contains("."))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDate(String cell) {
        return DATE_FORMATS.keySet().stream().anyMatch(pattern -> pattern.matcher(cell).find());
    }

    private static int modalWidth(List<String> lines, char separator) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (String line : lines) {
            int width = cells(line, separator).length;
            if (width >= 3) {
                counts.merge(width, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(2);
    }

    private static String separator(List<String> lines) {
        String best = ";";
        int bestScore = -1;
        for (String separator : SEPARATORS) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (String line : lines) {
                int count = cells(line, separator.charAt(0)).length - 1;
                if (count > 0) {
                    counts.merge(count, 1, Integer::sum);
                }
            }
            int score = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (score > bestScore) {
                best = separator;
                bestScore = score;
            }
        }
        return best;
    }

    /** One line split by the separator outside quotes; enough to guess, the parser reads the file properly. */
    private static String[] cells(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (char character : line.toCharArray()) {
            if (character == '"') {
                quoted = !quoted;
            } else if (character == separator && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        cells.add(cell.toString());
        return cells.toArray(String[]::new);
    }

    private static boolean isUtf8(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
