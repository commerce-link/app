package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message key the two new-offer pages use exists in both languages. */
class OfferCreateMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]");

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keysUsed() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (String template : new String[]{"offerContact.html", "newOffer_from_csv.html", "fragments/offer-create.html"}) {
            String source = Files.readString(Path.of("src/main/resources/templates", template));
            Matcher m = KEY.matcher(source);
            while (m.find()) {
                keys.add(m.group(1));
            }
            Matcher msg = Pattern.compile("#messages\\.msg\\('([a-zA-Z0-9_.]+)'[,)]").matcher(source);
            while (msg.find()) {
                keys.add(msg.group(1));
            }
        }
        // built from the controller's source ("basket", "offer", "template" or none) and passed to settings-form
        for (String source : new String[]{"offers", "template", "offer", "basket"}) {
            keys.add("offers.new.back." + source);
        }
        for (String source : new String[]{"manual", "template", "offer", "basket"}) {
            keys.add("offers.new.lead." + source);
        }
        keys.add("offers.new.errors.title");
        for (int column = 1; column <= 7; column++) {
            keys.add("offers.new.csv.col" + column + ".name");
            keys.add("offers.new.csv.col" + column + ".desc");
            keys.add("offers.new.csv.col" + column + ".sample");
        }
        return keys;
    }

    @Test
    void everyKeyExistsInPolishAndEnglish() throws Exception {
        // given
        Properties polish = load("messages_pl.properties");
        Properties english = load("messages_en.properties");

        // when
        Set<String> keys = keysUsed();

        // then
        assertThat(keys).contains("offers.new.title", "offers.new.lead.basket", "offers.new.csv.format", "offers.new.csv.file.too.large");
        assertThat(keys).allSatisfy(key -> {
            assertThat(polish).containsKey(key);
            assertThat(english).containsKey(key);
        });
    }
}
