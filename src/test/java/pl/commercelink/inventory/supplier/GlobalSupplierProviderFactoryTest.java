package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalSupplierProviderFactoryTest {

    private static final List<ProviderField> TOKEN_FIELD =
            List.of(new ProviderField("token", "API Token", ProviderField.FieldType.PASSWORD, true, ""));

    @Test
    void buildsProviderFromJsonGlobalSecret() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        SecretsManager secrets = mock(SecretsManager.class);
        when(secrets.getSecret("Stub")).thenReturn("{\"token\":\"abc\"}");

        // when
        Optional<SupplierProvider> provider = factoryFor(descriptor, secrets).get("Stub");

        // then
        assertThat(provider).isPresent();
        assertThat(descriptor.capturedConfig).containsEntry("token", "abc");
    }

    @Test
    void assignsBareStringSecretToTheFirstConfigurationField() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        SecretsManager secrets = mock(SecretsManager.class);
        when(secrets.getSecret("Stub")).thenReturn("raw-token");

        // when
        Optional<SupplierProvider> provider = factoryFor(descriptor, secrets).get("Stub");

        // then
        assertThat(provider).isPresent();
        assertThat(descriptor.capturedConfig).containsEntry("token", "raw-token");
    }

    @Test
    void returnsEmptyForUnknownSupplier() {
        // given
        SupplierProviderFactory supplierProviderFactory = mock(SupplierProviderFactory.class);
        SecretsManager secrets = mock(SecretsManager.class);
        when(supplierProviderFactory.getDescriptor("Ghost")).thenReturn(null);

        // when
        Optional<SupplierProvider> provider =
                new GlobalSupplierProviderFactory(supplierProviderFactory, secrets).get("Ghost");

        // then
        assertThat(provider).isEmpty();
    }

    @Test
    void returnsEmptyForOAuth2DescriptorBecauseItNeedsAStoreContext() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(List.of(),
                AuthConfig.OAuth2.of("https://api.example.com", "/auth", "/refresh", 3600));
        SecretsManager secrets = mock(SecretsManager.class);

        // when
        Optional<SupplierProvider> provider = factoryFor(descriptor, secrets).get("Stub");

        // then
        assertThat(provider).isEmpty();
    }

    @Test
    void failsLoudlyOnMalformedJsonSecret() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        SecretsManager secrets = mock(SecretsManager.class);
        when(secrets.getSecret("Stub")).thenReturn("{not-valid-json");

        // when / then
        assertThatThrownBy(() -> factoryFor(descriptor, secrets).get("Stub"))
                .isInstanceOf(RuntimeException.class);
    }

    private GlobalSupplierProviderFactory factoryFor(CapturingDescriptor descriptor, SecretsManager secrets) {
        SupplierProviderFactory supplierProviderFactory = mock(SupplierProviderFactory.class);
        when(supplierProviderFactory.getDescriptor(descriptor.name())).thenReturn(descriptor);
        return new GlobalSupplierProviderFactory(supplierProviderFactory, secrets);
    }

    private static final class CapturingDescriptor implements SupplierProviderDescriptor {

        private final List<ProviderField> fields;
        private final AuthConfig authConfig;
        private Map<String, String> capturedConfig;

        private CapturingDescriptor(List<ProviderField> fields, AuthConfig authConfig) {
            this.fields = fields;
            this.authConfig = authConfig;
        }

        @Override
        public SupplierProvider create(Map<String, String> configuration) {
            this.capturedConfig = configuration;
            return Optional::empty;
        }

        @Override
        public List<ProviderField> configurationFields() {
            return fields;
        }

        @Override
        public AuthConfig authConfig() {
            return authConfig;
        }

        @Override
        public FeedFormat feedFormat() {
            return new FeedFormat.Csv(row -> null, ';');
        }

        @Override
        public SupplierInfo supplierInfo() {
            return StubSupplierDescriptor.INFO;
        }
    }
}
