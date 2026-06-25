package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SupplierRegistryTest {

    private SupplierRegistry registry() {
        SupplierProviderFactory factory = new SupplierProviderFactory(new ProviderConfigurationManager(mock(SecretsManager.class)));
        return new SupplierRegistry(factory);
    }

    @Test
    void getReturnsSupplierInfoForDiscoveredSupplier() {
        // given
        SupplierRegistry registry = registry();

        // when
        SupplierInfo info = registry.get("Stub");

        // then
        assertThat(info.name()).isEqualTo("Stub");
    }

    @Test
    void getFallsBackToOtherForUnknownSupplier() {
        // given
        SupplierRegistry registry = registry();

        // when
        SupplierInfo info = registry.get("does-not-exist");

        // then
        assertThat(info.name()).isEqualTo("Other");
    }

    @Test
    void externalSupplierNamesExcludeWarehouseAndOther() {
        // given
        SupplierRegistry registry = registry();

        // when
        List<String> external = registry.getExternalSupplierNames();

        // then
        assertThat(external).contains("Stub", "Amazon").doesNotContain("Warehouse", "Other");
    }

    @Test
    void existsReflectsCatalogMembership() {
        // given
        SupplierRegistry registry = registry();

        // when / then
        assertThat(registry.exists("Warehouse")).isTrue();
        assertThat(registry.exists("does-not-exist")).isFalse();
    }
}
