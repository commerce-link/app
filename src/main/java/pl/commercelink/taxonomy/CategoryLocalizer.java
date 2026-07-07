package pl.commercelink.taxonomy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CategoryLocalizer {

    private final MessageSource messageSource;
    private final Locale fallbackLocale;

    CategoryLocalizer(MessageSource messageSource,
                      @Value("${commercelink.localization.fallback-locale:#{null}}") Locale fallbackLocale) {
        this.messageSource = messageSource;
        this.fallbackLocale = fallbackLocale;
    }

    public String localize(String categoryKey, String suffix) {
        if (categoryKey == null) return "";
        String key = "ProductCategory." + categoryKey;
        if (suffix != null && !suffix.isEmpty()) key += "." + suffix;
        return messageSource.getMessage(key, null, categoryKey, resolveLocale());
    }

    private Locale resolveLocale() {
        if (LocaleContextHolder.getLocaleContext() != null) {
            return LocaleContextHolder.getLocale();
        }
        return fallbackLocale != null ? fallbackLocale : Locale.getDefault();
    }
}
