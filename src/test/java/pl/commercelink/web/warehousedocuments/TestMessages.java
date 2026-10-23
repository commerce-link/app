package pl.commercelink.web.warehousedocuments;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

public final class TestMessages {

    private TestMessages() { }

    public static MessageSource polish() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
