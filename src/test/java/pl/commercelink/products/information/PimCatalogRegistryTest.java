package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PimCatalogRegistryTest {

    private final PimCatalogDescriptor realAdapter = descriptor("commercelink-pim");
    private final PimCatalogDescriptor devAdapter = descriptor("pim-dev");

    @Test
    void failsFastWhenNoDescriptorIsPresent() {
        // when / then
        assertThatThrownBy(() -> PimCatalogRegistry.resolveDescriptor(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No PimCatalogDescriptor found on classpath");
    }

    @Test
    void resolvesTheOnlyDescriptorPresent() {
        // when
        PimCatalogDescriptor resolved = PimCatalogRegistry.resolveDescriptor(List.of(devAdapter));

        // then
        assertThat(resolved).isSameAs(devAdapter);
    }

    @Test
    void failsFastNamingBothAdaptersWhenMoreThanOneIsPresent() {
        // when / then
        assertThatThrownBy(() -> PimCatalogRegistry.resolveDescriptor(List.of(devAdapter, realAdapter)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commercelink-pim")
                .hasMessageContaining("pim-dev");
    }

    private static PimCatalogDescriptor descriptor(String name) {
        return new PimCatalogDescriptor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String displayName() {
                return name;
            }

            @Override
            public List<ProviderField> configurationFields() {
                return List.of();
            }

            @Override
            public PimCatalog create(Map<String, String> configuration) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
