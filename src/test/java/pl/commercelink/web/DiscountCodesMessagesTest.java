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

class DiscountCodesMessagesTest {

    private static final String PREFIX = "store.discounts";

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    @Test
    void messagesWithArgumentsNameTheirValuesInBothLanguages() {
        // given
        ResourceBundleMessageSource messages = messages();

        for (Locale locale : List.of(Locale.of("pl"), Locale.ENGLISH)) {
            // when
            List<String> texts = List.of(
                    messages.getMessage("store.discounts.created", new Object[]{"LATO-10"}, locale),
                    messages.getMessage("store.discounts.deleted", new Object[]{"LATO-10"}, locale),
                    messages.getMessage("store.discounts.delete.title", new Object[]{"LATO-10"}, locale),
                    messages.getMessage("store.discounts.delete.refused", new Object[]{"LATO-10"}, locale),
                    messages.getMessage("store.discounts.toggled.on", new Object[]{"LATO-10"}, locale),
                    messages.getMessage("store.discounts.toggled.off", new Object[]{"LATO-10"}, locale),
                    messages.getMessage("store.discounts.list.minimum", new Object[]{"500,00"}, locale),
                    messages.getMessage("store.discounts.list.targetMore", new Object[]{"Kategorie", 2}, locale),
                    messages.getMessage("store.discounts.uses.limited", new Object[]{6, 100}, locale),
                    messages.getMessage("store.discounts.details.uses.limited", new Object[]{6, 100}, locale),
                    messages.getMessage("store.discounts.validity.range", new Object[]{"01.10.2026", "31.12.2026"}, locale),
                    messages.getMessage("store.discounts.pick.categories.count", new Object[]{3, 12}, locale),
                    messages.getMessage("store.discounts.picked.remove", new Object[]{"ASUS"}, locale),
                    messages.getMessage("store.discounts.pick.choose", new Object[]{"ASUS"}, locale),
                    messages.getMessage("store.discounts.pick.qty", new Object[]{12}, locale),
                    messages.getMessage("store.discounts.pick.products.count", new Object[]{4, 186}, locale),
                    messages.getMessage("store.discounts.pick.brands.count", new Object[]{2, 31}, locale),
                    messages.getMessage("store.discounts.pick.submit.count", new Object[]{3}, locale),
                    messages.getMessage("store.discounts.summary.products", new Object[]{3}, locale),
                    messages.getMessage("store.discounts.summary.brands", new Object[]{"ASUS, MSI"}, locale));

            // then
            assertThat(texts).as(locale.toString()).noneMatch(text -> text.contains("{") || text.contains("}"));
            assertThat(texts.get(0)).as(locale.toString()).contains("LATO-10");
            assertThat(texts.get(7)).as(locale.toString()).contains("Kategorie").contains("+2");
            assertThat(texts.get(8)).as(locale.toString()).isEqualTo("6 / 100");
        }
    }

    @Test
    void everyDiscountKeyExistsInBothLanguages() {
        // when
        Set<String> polish = discountKeys(Locale.forLanguageTag("pl"));
        Set<String> english = discountKeys(Locale.ENGLISH);

        // then
        assertThat(polish).isNotEmpty().isEqualTo(english);
    }

    @Test
    void noDiscountKeyIsDefinedTwice() throws IOException {
        for (String file : List.of("messages_pl.properties", "messages_en.properties")) {
            // when
            List<String> keys = discountLines(file).stream()
                    .map(line -> line.substring(0, line.indexOf('=')))
                    .toList();

            // then
            assertThat(keys).as(file).doesNotHaveDuplicates();
        }
    }

    private static Set<String> discountKeys(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        return bundle.keySet().stream().filter(key -> key.startsWith(PREFIX)).collect(Collectors.toSet());
    }

    private static List<String> discountLines(String file) throws IOException {
        try (InputStream stream = DiscountCodesMessagesTest.class.getClassLoader().getResourceAsStream(file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith(PREFIX) && line.contains("="))
                    .toList();
        }
    }
}
