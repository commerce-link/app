package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerClosingMessagesTest {

    private static final Pattern KEY = Pattern.compile("\"(reports\\.stockLedger\\.[a-zA-Z0-9_.]+)\"|#\\{(reports\\.stockLedger\\.[a-zA-Z0-9_.]+)[(}]");

    private static Properties load(String name) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/" + name), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void everyKeyOfTheMonthClosingExistsInBothLanguages() throws Exception {
        // given
        Set<String> keys = new TreeSet<>();
        for (Path file : List.of(
                Path.of("src/main/resources/templates/reports.html"),
                Path.of("src/main/java/pl/commercelink/web/FinancialReportsController.java"),
                Path.of("src/main/java/pl/commercelink/web/reports/StockLedgerClosingBlocker.java"),
                Path.of("src/main/java/pl/commercelink/warehouse/builtin/StockLedgerMonthClosing.java"))) {
            Matcher m = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (m.find()) {
                keys.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }

        // when
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // then
        assertThat(keys).contains("reports.stockLedger.closing.missing.sync", "reports.stockLedger.closing.regenerated");
        assertThat(keys.stream().filter(k -> !pl.containsKey(k)).toList()).as("missing in PL").isEmpty();
        assertThat(keys.stream().filter(k -> !en.containsKey(k)).toList()).as("missing in EN").isEmpty();
    }
}
