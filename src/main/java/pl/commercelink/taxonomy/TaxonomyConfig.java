package pl.commercelink.taxonomy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TaxonomyCategoryMatchProperties.class)
class TaxonomyConfig {
}
