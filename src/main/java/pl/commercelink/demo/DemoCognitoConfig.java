package pl.commercelink.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "app.demo.registration.enabled", havingValue = "true")
public class DemoCognitoConfig {

    @Value("${amazon.aws.region}")
    private String region;

    @Bean
    @ConditionalOnProperty(name = "application.env", havingValue = "prod")
    public CognitoIdentityProviderClient prodCognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
    public CognitoIdentityProviderClient localCognitoClient(@Value("${localstack.aws.endpoint}") String endpoint) {
        return CognitoIdentityProviderClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
    }
}
