package pl.commercelink.starter.csv;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CSVLoader {

    private static final Logger log = LoggerFactory.getLogger(CSVLoader.class);

    public static final Character DEFAULT_SEPARATOR = ';';

    private final Reader reader;

    public CSVLoader(Reader reader) {
        this.reader = reader;
    }

    public Pair<String[], List<String[]>> readHeadersAndRows(Character separator) {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(separator)
                .withIgnoreQuotations(false)
                .build();

        try (CSVReader csvReader = new CSVReaderBuilder(reader)
                .withSkipLines(0)
                .withCSVParser(parser)
                .build()) {
            String[] headers = csvReader.readNext();
            List<String[]> rows = new ArrayList<>();
            String[] row;
            while (true) {
                try {
                    row = csvReader.readNext();
                    if (row == null) break;
                    rows.add(row);
                } catch (CsvMalformedLineException e) {
                    long lineNumber = csvReader.getLinesRead();
                    log.warn("Malformed CSV at line {} – {}", lineNumber, e.getMessage());
                }
            }
            return Pair.of(headers != null ? headers : new String[0], rows);
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Error reading CSV file", e);
        }
    }

    public void readRows(Character separator, Consumer<String[]> rowConsumer) {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(separator)
                .withIgnoreQuotations(false)
                .build();

        try (CSVReader csvReader = new CSVReaderBuilder(reader)
                .withSkipLines(1)
                .withCSVParser(parser)
                .build()) {

            while (true) {
                try {
                    String[] nextLine = csvReader.readNext();
                    if (nextLine == null) break;
                    rowConsumer.accept(nextLine);
                } catch (CsvMalformedLineException e) {
                    log.warn("Skipping malformed CSV row at line {}: {}", csvReader.getLinesRead(), e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Error reading CSV file", e);
        }
    }

}
