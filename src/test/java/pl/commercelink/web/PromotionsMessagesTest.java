package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionsMessagesTest {

    private static final String PREFIX = "store.promotions";

    @Test
    void messagesWithArgumentsNameTheirValuesInBothLanguages() {
        // given
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        for (Locale locale : List.of(Locale.of("pl"), Locale.ENGLISH)) {
            // when
            List<String> texts = List.of(
                    messages.getMessage("store.promotions.created", new Object[]{"Duży koszyk"}, locale),
                    messages.getMessage("store.promotions.deleted", new Object[]{"Duży koszyk"}, locale),
                    messages.getMessage("store.promotions.delete.title", new Object[]{"Duży koszyk"}, locale),
                    messages.getMessage("store.promotions.toggled.on", new Object[]{"Duży koszyk"}, locale),
                    messages.getMessage("store.promotions.toggled.off", new Object[]{"Duży koszyk"}, locale),
                    messages.getMessage("store.promotions.list.quantity", new Object[]{2}, locale),
                    messages.getMessage("store.promotions.list.minimum", new Object[]{"5 000,00"}, locale),
                    messages.getMessage("store.promotions.list.more", new Object[]{2}, locale),
                    messages.getMessage("store.promotions.list.target", new Object[]{"ASUS"}, locale),
                    messages.getMessage("store.promotions.details.condition.quantity", new Object[]{"Marki", 2}, locale));

            // then
            assertThat(texts).as(locale.toString()).noneMatch(text -> text.contains("{") || text.contains("}"));
            assertThat(texts.get(0)).as(locale.toString()).contains("Duży koszyk");
            assertThat(texts.get(9)).as(locale.toString()).contains("Marki").contains("2");
        }
    }

    @Test
    void everyPromotionKeyExistsInBothLanguages() {
        // when
        Set<String> polish = keys(Locale.forLanguageTag("pl"));
        Set<String> english = keys(Locale.ENGLISH);

        // then
        assertThat(polish).isNotEmpty().isEqualTo(english);
    }

    @Test
    void noPromotionKeyIsDefinedTwice() throws IOException {
        for (String file : List.of("messages_pl.properties", "messages_en.properties")) {
            // when
            List<String> keys = lines(file).stream()
                    .map(line -> line.substring(0, line.indexOf('=')))
                    .toList();

            // then
            assertThat(keys).as(file).doesNotHaveDuplicates();
        }
    }

    private static Set<String> keys(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        return bundle.keySet().stream().filter(key -> key.startsWith(PREFIX)).collect(Collectors.toSet());
    }

    private static List<String> lines(String file) throws IOException {
        try (InputStream stream = PromotionsMessagesTest.class.getClassLoader().getResourceAsStream(file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith(PREFIX) && line.contains("="))
                    .toList();
        }
    }
}
