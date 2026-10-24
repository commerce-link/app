package pl.commercelink.web.errorpage;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.DefaultErrorViewResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.commercelink.stores.StoresRepository;

@Configuration
class ErrorPageConfiguration {

    // Spring Boot's own DefaultErrorViewResolver is created only while the context has no ErrorViewResolver, so the
    // customer pages' resolver wraps one: without it a missing dashboard page fell back to the Whitelabel page.
    @Bean
    PublicErrorViewResolver publicErrorViewResolver(StoresRepository storesRepository, ApplicationContext context) {
        return new PublicErrorViewResolver(storesRepository,
                new DefaultErrorViewResolver(context, new WebProperties.Resources()));
    }
}
