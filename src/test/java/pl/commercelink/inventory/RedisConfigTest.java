package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import pl.commercelink.starter.secrets.ParameterStore;
import pl.commercelink.starter.secrets.SecretsManager;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    @Test
    void prodFactoryUsesEndpointFromParameterStoreAndTokenFromSecretsWithTls() {
        // given
        ParameterStore parameterStore = mock(ParameterStore.class);
        SecretsManager secretsManager = mock(SecretsManager.class);
        when(parameterStore.getParameter("commercelink", "redis", "connection", RedisConnectionProperties.class))
                .thenReturn(Optional.of(new RedisConnectionProperties("my-redis.cache.amazonaws.com", 6379)));
        when(secretsManager.getSecret("commercelink/redis/auth-token")).thenReturn("s3cr3t-token");

        // when
        LettuceConnectionFactory factory =
                new RedisConfig().prodRedisConnectionFactory(parameterStore, secretsManager,
                        Duration.ofSeconds(2), Duration.ofSeconds(1));

        // then
        RedisStandaloneConfiguration standalone = factory.getStandaloneConfiguration();
        assertEquals("my-redis.cache.amazonaws.com", standalone.getHostName());
        assertEquals(6379, standalone.getPort());
        assertEquals("s3cr3t-token", new String(standalone.getPassword().get()));
        assertTrue(factory.getClientConfiguration().isUseSsl());
        assertEquals(Duration.ofSeconds(2), factory.getClientConfiguration().getCommandTimeout());
    }

    @Test
    void prodFactoryFailsFastWhenConnectionParameterMissing() {
        // given
        ParameterStore parameterStore = mock(ParameterStore.class);
        SecretsManager secretsManager = mock(SecretsManager.class);
        when(parameterStore.getParameter("commercelink", "redis", "connection", RedisConnectionProperties.class))
                .thenReturn(Optional.empty());

        // when / then
        assertThrows(IllegalStateException.class,
                () -> new RedisConfig().prodRedisConnectionFactory(parameterStore, secretsManager,
                        Duration.ofSeconds(2), Duration.ofSeconds(1)));
    }
}
