package pl.commercelink.products;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductGroupLocalizationTest {

    private final ProductGroupLocalization localizer =
            new ProductGroupLocalization(messageSource());

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void nameReturnsPolishLabelForPlLocale() {
        LocaleContextHolder.setLocale(new Locale("pl"));
        assertEquals("Laptopy i komputery", localizer.name(ProductGroup.Computers));
    }

    @Test
    void fallsBackToPolishWhenLocaleIsJvmDefault() {
        LocaleContextHolder.setLocale(Locale.getDefault());
        assertEquals("Laptopy i komputery", localizer.name(ProductGroup.Computers));
    }

    @Test
    void returnsEmptyStringWhenKeyMissing() {
        LocaleContextHolder.setLocale(new Locale("pl"));
        ProductGroupLocalization emptyLocalizer =
                new ProductGroupLocalization(emptyMessageSource());
        assertEquals("", emptyLocalizer.name(ProductGroup.Computers));
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
