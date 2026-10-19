package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StorePaymentsMessagesTest {

    @Test
    void theLastGatewayWarningNamesTheGatewayInBothLanguages() {
        // given: a single ' in a message with arguments would quote the rest of it, {0} included
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        Object[] gateway = {"PayU"};

        // when
        String english = messages.getMessage("store.payments.gateway.disconnect.message.last", gateway, Locale.ENGLISH);
        String polish = messages.getMessage("store.payments.gateway.disconnect.message.last", gateway, Locale.of("pl"));

        // then
        assertThat(english).contains("The access details of PayU will be deleted.").contains("offer’s \"Pay\"")
                .doesNotContain("{0}");
        assertThat(polish).contains("Dane dostępu PayU zostaną usunięte.");
    }
}
