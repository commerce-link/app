package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PimCatalogRegistryTest {

    private final PimCatalogDescriptor realAdapter = descriptor("commercelink-pim", Map.of());
    private final PimCatalogDescriptor devAdapter = descriptor("pim-dev", Map.of("dev", "true"));

    @Test
    void selectsNothingWhenNoDescriptorsArePresent() {
        // when
        Optional<PimCatalogDescriptor> selected = PimCatalogRegistry.selectDescriptor(List.of());

        // then
        assertThat(selected).isEmpty();
    }

    @Test
    void selectsTheOnlyDescriptorEvenWhenItIsADevAdapter() {
        // when
        Optional<PimCatalogDescriptor> selected = PimCatalogRegistry.selectDescriptor(List.of(devAdapter));

        // then
        assertThat(selected).contains(devAdapter);
    }

    @Test
    void prefersRealAdapterOverDevAdapterRegardlessOfOrder() {
        // when
        Optional<PimCatalogDescriptor> devFirst = PimCatalogRegistry.selectDescriptor(List.of(devAdapter, realAdapter));
        Optional<PimCatalogDescriptor> realFirst = PimCatalogRegistry.selectDescriptor(List.of(realAdapter, devAdapter));

        // then
        assertThat(devFirst).contains(realAdapter);
        assertThat(realFirst).contains(realAdapter);
    }

    private static PimCatalogDescriptor descriptor(String name, Map<String, String> metadata) {
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

            @Override
            public Map<String, String> metadata() {
                return metadata;
            }
        };
    }
}
