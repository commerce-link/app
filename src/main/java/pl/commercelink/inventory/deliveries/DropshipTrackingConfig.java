package pl.commercelink.inventory.deliveries;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DropshipTrackingProperties.class)
class DropshipTrackingConfig {
}
