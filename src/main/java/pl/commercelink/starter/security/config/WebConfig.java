package pl.commercelink.starter.security.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pl.commercelink.starter.security.StoreAccessInterceptor;
import pl.commercelink.starter.security.StoreApiKeyAuthorizationInterceptor;
import pl.commercelink.starter.security.interceptor.ApiGatewayIdInterceptor;

@Configuration
public class WebConfig {

    @Value("${app.cors}")
    private String cors;

    @Autowired
    private ApiGatewayIdInterceptor apiGatewayIdInterceptor;

    @Autowired
    private StoreApiKeyAuthorizationInterceptor storeApiKeyAuthorizationInterceptor;

    @Autowired
    private StoreAccessInterceptor storeAccessInterceptor;

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

                registry.addInterceptor(storeAccessInterceptor)
                        .addPathPatterns("/dashboard/store/**")
                        .excludePathPatterns(
                                "/dashboard/store/branding/**",
                                "/dashboard/store/invoicing/**",
                                "/dashboard/store/warehouse/**",
                                "/dashboard/store/shipping/**",
                                "/dashboard/store/notification/**",
                                "/dashboard/store/fulfilment/**",
                                "/dashboard/store/payments/**",
                                "/dashboard/store/marketplaces/**",
                                "/dashboard/store/company-details/**",
                                "/dashboard/store/email-templates/**",
                                "/dashboard/store/rma/**",
                                "/dashboard/store/rma-centers/**",
                                "/dashboard/store/report/**",
                                "/dashboard/store/integrations/**"
                        );
            }

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins(cors.split(","));
            }

        };
    }

}
