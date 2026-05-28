package pl.commercelink.products;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.Locale;

@Component
public class ProductGroupLocalization {

    public static ProductGroupLocalization INSTANCE;

    private final MessageSource messageSource;

    public ProductGroupLocalization(MessageSource messageSource) {
        this.messageSource = messageSource;
        INSTANCE = this;
    }

    public String name(ProductGroup g) {
        return resolve("product.group." + g.name());
    }

    private String resolve(String code) {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale == Locale.getDefault()) {
            locale = new Locale("pl");
        }
        return messageSource.getMessage(code, null, "", locale);
    }
}
