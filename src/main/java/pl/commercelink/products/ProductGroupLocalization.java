package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ProductGroupLocalization {

    private final MessageSource messageSource;

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
