package pl.commercelink.starter.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pl.commercelink.starter.security.StoreAccessInterceptor;
import pl.commercelink.starter.security.StoreApiKeyAuthorizationInterceptor;
import pl.commercelink.registration.EmailVerificationInterceptor;
import pl.commercelink.starter.security.interceptor.ApiGatewayIdInterceptor;
import pl.commercelink.web.activity.DashboardReadOnlyInterceptor;
import pl.commercelink.web.activity.PublicStoreActivityInterceptor;

@Configuration
@RequiredArgsConstructor
public class WebConfig {

    @Value("${app.cors}")
    private String cors;

    private final ApiGatewayIdInterceptor apiGatewayIdInterceptor;

    private final StoreApiKeyAuthorizationInterceptor storeApiKeyAuthorizationInterceptor;

    private final StoreAccessInterceptor storeAccessInterceptor;

    private final EmailVerificationInterceptor emailVerificationInterceptor;

    private final DashboardReadOnlyInterceptor dashboardReadOnlyInterceptor;

    private final PublicStoreActivityInterceptor publicStoreActivityInterceptor;

    @Bean
    public WebMvcConfigurer corsConfigurer()
    {
        return new WebMvcConfigurer() {

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                WebMvcConfigurer.super.addInterceptors(registry);

                registry.addInterceptor(apiGatewayIdInterceptor)
                        .addPathPatterns("/Global/**")
                        .addPathPatterns("/Store/**")
                        .excludePathPatterns("/store/*/individual/offer/**");

                registry.addInterceptor(storeApiKeyAuthorizationInterceptor)
                        .addPathPatterns("/Store/*/Catalog/**")
                        .excludePathPatterns("/store/*/individual/offer/**");

                // Webhooks report what already happened at the provider: a basket paid before the deactivation still
                // becomes an order, a delivered parcel or a fiscalised receipt is still recorded. Refusing them would
                // only lose that, and Fakturownia switches a failing webhook off for the whole account.
                registry.addInterceptor(publicStoreActivityInterceptor)
                        .addPathPatterns("/Store/*/**", "/store/*/client/**", "/store/*/individual/**")
                        .excludePathPatterns("/Store/*/Webhooks/**");

                registry.addInterceptor(emailVerificationInterceptor)
                        .addPathPatterns("/dashboard/**");

                // After the e-mail check, so an owner who has not confirmed the address yet is sent there first.
                registry.addInterceptor(dashboardReadOnlyInterceptor)
                        .addPathPatterns("/dashboard/**");

                registry.addInterceptor(storeAccessInterceptor)
                        .addPathPatterns("/dashboard/store/**")
                        .excludePathPatterns(
                                "/dashboard/store/branding/**",
                                "/dashboard/store/categories/**",
                                "/dashboard/store/invoicing/**",
                                "/dashboard/store/receipts/**",
                                "/dashboard/store/warehouse/**",
                                "/dashboard/store/shipping/**",
                                "/dashboard/store/notification/**",
                                "/dashboard/store/fulfilment/**",
                                "/dashboard/store/suppliers/**",
                                "/dashboard/store/payments/**",
                                "/dashboard/store/marketplaces/**",
                                "/dashboard/store/company-details/**",
                                "/dashboard/store/email-templates/**",
                                "/dashboard/store/rma/**",
                                "/dashboard/store/rma-centers/**",
                                "/dashboard/store/report/**"
                        );
            }

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins(cors.split(","));
            }

        };
    }

}
