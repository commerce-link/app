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

/** Every message key of the supplier selection page exists in both languages, and the old page's keys are gone. */
class FulfilmentSelectMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]|\"((?:fulfilment\\.(?:select|queue)|intro\\.fulfilmentSelect)\\.[a-zA-Z0-9_.]+)\"");

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (Path file : List.of(Path.of("src/main/resources/templates/fulfilment.html"),
                Path.of("src/main/java/pl/commercelink/web/fulfilment/FulfilmentSelectPage.java"),
                Path.of("src/main/java/pl/commercelink/web/fulfilment/FulfilmentSelectPageFactory.java"),
                Path.of("src/main/java/pl/commercelink/web/OrdersFulfilmentController.java"))) {
            Matcher m = KEY.matcher(Files.readString(file));
            while (m.find()) {
                keys.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
        keys.removeIf(k -> k.endsWith("."));
        for (int i = 1; i <= 4; i++) {
            keys.add("intro.fulfilmentSelect.item" + i);
            keys.add("intro.fulfilmentSelect.item" + i + ".text");
        }
        keys.add("intro.fulfilmentSelect.title");
        keys.add("intro.fulfilmentSelect.lead");
        return keys;
    }

    @Test
    void everyKeyExistsInPolishAndEnglish() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // when
        Set<String> used = keysUsed();

        // then
        assertThat(used).contains("fulfilment.select.commit", "fulfilment.select.saved", "fulfilment.select.kind.warehouse",
                "fulfilment.select.missing.reason.noOffer");
        assertThat(used).allSatisfy(key -> {
            assertThat(pl.getProperty(key)).as("pl " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        });
    }

    @Test
    void theOldPageKeysAreRemoved() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // then
        for (String key : List.of("fulfilment.title", "fulfilment.info.note", "fulfilment.coverage", "fulfilment.filters",
                "fulfilment.variants.custom", "fulfilment.clear.selection", "fulfilment.target", "fulfilment.realizes",
                "fulfilment.redundant", "fulfilment.partial.warning", "fulfilment.total.net", "fulfilment.empty.selection")) {
            assertThat(pl.getProperty(key)).as("pl " + key).isNull();
            assertThat(en.getProperty(key)).as("en " + key).isNull();
        }
    }

    @Test
    void englishAmountsUseTheZlotySignLikeTheRestOfTheSelectionTexts() throws Exception {
        // given
        Properties en = load("messages_en.properties");

        // then
        for (String key : List.of("fulfilment.select.money", "fulfilment.select.unit.perPiece", "fulfilment.select.category.chosen",
                "fulfilment.select.alt.dearer", "fulfilment.select.alt.fold", "fulfilment.select.filter.range")) {
            assertThat(en.getProperty(key)).as("en " + key).contains("zł").doesNotContain("PLN");
        }
    }

    @Test
    void theReadabilityKeysAreUsedAndTheHeaderCheckboxKeysAreGone() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // when
        Set<String> used = keysUsed();

        // then
        assertThat(used).contains("fulfilment.select.unit.pieces", "fulfilment.select.unit.perPiece", "fulfilment.select.money",
                "fulfilment.select.toggle.on", "fulfilment.select.toggle.off", "fulfilment.select.toggle.alt", "fulfilment.select.toggle.kept",
                "fulfilment.select.list.expand", "fulfilment.select.list.collapse", "fulfilment.select.list.selectVisible",
                "fulfilment.select.list.clearVisible", "fulfilment.select.alt.dearer", "fulfilment.select.alt.fold",
                "fulfilment.select.orders.filter", "fulfilment.select.orders.filter.split", "fulfilment.select.ratio",
                "fulfilment.select.category.chosen", "fulfilment.select.mfn", "fulfilment.select.offer.name",
                "fulfilment.select.covered.off", "fulfilment.select.alt.fold.plain");
        for (String key : List.of("fulfilment.select.all", "fulfilment.select.column.price.short", "fulfilment.select.category.count",
                "fulfilment.select.offer.label")) {
            assertThat(pl.getProperty(key)).as("pl " + key).isNull();
            assertThat(en.getProperty(key)).as("en " + key).isNull();
        }
    }
}
