package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ProductCategoryLocalization {

    private final MessageSource messageSource;

    public String name(ProductCategory c) {
        return plural(c);
    }

    public String singular(ProductCategory c) {
        return resolve("product.category." + c.name() + ".singular");
    }

    public String plural(ProductCategory c) {
        return resolve("product.category." + c.name() + ".plural");
    }

    private String resolve(String code) {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale == Locale.getDefault()) {
            locale = new Locale("pl");
        }
        return messageSource.getMessage(code, null, "", locale);
    }
}
