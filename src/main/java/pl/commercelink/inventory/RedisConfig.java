package pl.commercelink.inventory;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import pl.commercelink.starter.secrets.ParameterStore;
import pl.commercelink.starter.secrets.SecretsManager;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "application.env", havingValue = "prod")
    public LettuceConnectionFactory prodRedisConnectionFactory(ParameterStore parameterStore,
                                                               SecretsManager secretsManager,
                                                               @Value("${spring.data.redis.timeout}") Duration commandTimeout,
                                                               @Value("${spring.data.redis.connect-timeout}") Duration connectTimeout) {
        RedisConnectionProperties connection = parameterStore
                .getParameter("commercelink", "redis", "connection", RedisConnectionProperties.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing SSM parameter /commercelink/redis/connection"));

        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration(connection.host(), connection.port());
        standalone.setPassword(RedisPassword.of(secretsManager.getSecret("commercelink/redis/auth-token")));

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .build();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(commandTimeout)
                .useSsl()
                .build();
        return new LettuceConnectionFactory(standalone, clientConfig);
    }
}
