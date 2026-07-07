package pl.commercelink.taxonomy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryLocalizerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    private final StaticMessageSource messageSource = new StaticMessageSource();
    private final CategoryLocalizer localizer = new CategoryLocalizer(messageSource, POLISH);

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void localizesKnownCategoryKeyWithSuffix() {
        // given
        messageSource.addMessage("ProductCategory.Laptops.plural", POLISH, "Laptopy");
        LocaleContextHolder.setLocale(POLISH);

        // when / then
        assertThat(localizer.localize("Laptops", "plural")).isEqualTo("Laptopy");
    }

    @Test
    void fallsBackToRawKeyForUnknownCategory() {
        // given
        LocaleContextHolder.setLocale(POLISH);

        // when / then
        assertThat(localizer.localize("Smartwatches", "plural")).isEqualTo("Smartwatches");
    }

    @Test
    void returnsEmptyStringForNullKey() {
        // when / then
        assertThat(localizer.localize(null, "plural")).isEmpty();
    }

    @Test
    void usesFallbackLocaleWhenNoLocaleContext() {
        // given
        messageSource.addMessage("ProductCategory.Laptops.singular", POLISH, "Laptop");

        // when / then
        assertThat(localizer.localize("Laptops", "singular")).isEqualTo("Laptop");
    }

    @Test
    void localizesWithoutSuffix() {
        // given
        messageSource.addMessage("ProductCategory.Laptops", POLISH, "Laptop");
        LocaleContextHolder.setLocale(POLISH);

        // when / then
        assertThat(localizer.localize("Laptops", null)).isEqualTo("Laptop");
    }
}
