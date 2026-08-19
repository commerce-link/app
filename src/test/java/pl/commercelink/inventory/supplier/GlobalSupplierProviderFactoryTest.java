package pl.commercelink.inventory.supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSupplierProviderFactoryTest {

    private static final List<ProviderField> TOKEN_FIELD =
            List.of(new ProviderField("token", "API Token", ProviderField.FieldType.PASSWORD, true, ""));

    @Mock
    private SupplierProviderFactory supplierProviderFactory;

    @Mock
    private SecretsManager secrets;

    @Test
    void buildsProviderFromJsonGlobalSecret() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        when(supplierProviderFactory.getDescriptor("Stub")).thenReturn(descriptor);
        when(secrets.getSecret("Stub")).thenReturn("{\"token\":\"abc\"}");

        // when
        Optional<SupplierProvider> provider = factory().get("Stub");

        // then
        assertThat(provider).isPresent();
        assertThat(descriptor.capturedConfig).containsEntry("token", "abc");
    }

    @Test
    void assignsBareStringSecretToTheFirstConfigurationField() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        when(supplierProviderFactory.getDescriptor("Stub")).thenReturn(descriptor);
        when(secrets.getSecret("Stub")).thenReturn("raw-token");

        // when
        Optional<SupplierProvider> provider = factory().get("Stub");

        // then
        assertThat(provider).isPresent();
        assertThat(descriptor.capturedConfig).containsEntry("token", "raw-token");
    }

    @Test
    void returnsEmptyForUnknownSupplier() {
        // when
        Optional<SupplierProvider> provider = factory().get("Ghost");

        // then
        assertThat(provider).isEmpty();
    }

    @Test
    void returnsEmptyForOAuth2DescriptorBecauseItNeedsAStoreContext() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(List.of(),
                AuthConfig.OAuth2.of("https://api.example.com", "/auth", "/refresh", 3600));
        when(supplierProviderFactory.getDescriptor("Stub")).thenReturn(descriptor);

        // when
        Optional<SupplierProvider> provider = factory().get("Stub");

        // then
        assertThat(provider).isEmpty();
    }

    @Test
    void propagatesExceptionWhenSecretIsMissing() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        when(supplierProviderFactory.getDescriptor("Stub")).thenReturn(descriptor);
        when(secrets.getSecret("Stub")).thenThrow(new RuntimeException("no secret found"));

        // when / then
        assertThatThrownBy(() -> factory().get("Stub"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("no secret found");
    }

    @Test
    void failsLoudlyOnMalformedJsonSecret() {
        // given
        CapturingDescriptor descriptor = new CapturingDescriptor(TOKEN_FIELD, AuthConfig.None.INSTANCE);
        when(supplierProviderFactory.getDescriptor("Stub")).thenReturn(descriptor);
        when(secrets.getSecret("Stub")).thenReturn("{not-valid-json");

        // when / then
        assertThatThrownBy(() -> factory().get("Stub"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    private GlobalSupplierProviderFactory factory() {
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
