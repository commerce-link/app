package pl.commercelink.products;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCategoryLocalizationTest {

    private final ProductCategoryLocalization localizer =
            new ProductCategoryLocalization(messageSource());

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void singularReturnsPolishLabelForPlLocale() {
        LocaleContextHolder.setLocale(new Locale("pl"));
        assertEquals("Laptop", localizer.singular(ProductCategory.Laptops));
    }

    @Test
    void pluralReturnsPolishLabelForPlLocale() {
        LocaleContextHolder.setLocale(new Locale("pl"));
        assertEquals("Laptopy", localizer.plural(ProductCategory.Laptops));
    }

    @Test
    void nameReturnsPluralForm() {
        LocaleContextHolder.setLocale(new Locale("pl"));
        assertEquals("Laptopy", localizer.name(ProductCategory.Laptops));
    }

    @Test
    void fallsBackToPolishWhenLocaleIsJvmDefault() {
        LocaleContextHolder.setLocale(Locale.getDefault());
        assertEquals("Laptop", localizer.singular(ProductCategory.Laptops));
    }

    @Test
    void returnsEmptyStringWhenKeyMissing() {
        LocaleContextHolder.setLocale(new Locale("pl"));
        ProductCategoryLocalization emptyLocalizer =
                new ProductCategoryLocalization(emptyMessageSource());
        assertEquals("", emptyLocalizer.singular(ProductCategory.Laptops));
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames("messages_pl", "messages_en");
        ms.setDefaultEncoding("UTF-8");
        return ms;
    }

    private static ResourceBundleMessageSource emptyMessageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setDefaultEncoding("UTF-8");
        return ms;
    }
}
