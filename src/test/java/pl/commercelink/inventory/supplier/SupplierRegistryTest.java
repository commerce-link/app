package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertThat(external).contains("Stub").doesNotContain("Warehouse", "Other");
    }

    @Test
    void existsReflectsCatalogMembership() {
        // given
        SupplierRegistry registry = registry();

        // when / then
        assertThat(registry.exists("Warehouse")).isTrue();
        assertThat(registry.exists("does-not-exist")).isFalse();
    }

    @Test
    void getResolvesATokenedIdentityToItsTypeAndKeepsTheIdentityAsName() {
        // given
        SupplierRegistry registry = registry();

        // when
        SupplierInfo info = registry.get("Stub-k7f3a9c2");

        // then
        assertThat(info.name()).isEqualTo("Stub-k7f3a9c2");
        assertThat(info.type()).isEqualTo(StubSupplierDescriptor.INFO.type());
        assertThat(info.shippingPolicy()).isEqualTo(StubSupplierDescriptor.INFO.shippingPolicy());
    }

    @Test
    void existsLooksAtTheTypeOfTheIdentity() {
        // given
        SupplierRegistry registry = registry();

        // when / then
        assertThat(registry.exists("Stub-k7f3a9c2")).isTrue();
        assertThat(registry.exists("Nope-k7f3a9c2")).isFalse();
        assertThat(registry.exists("manual:Asus")).isFalse();
    }

    @Test
    void getFallsBackToManualInfoForATokenedManualIdentity() {
        // when
        SupplierInfo info = registry().get("manual-k7f3a9c2");

        // then
        assertThat(info.name()).isEqualTo("manual-k7f3a9c2");
        assertThat(info.type()).isEqualTo(SupplierType.Distributor);
    }

    @Test
    void refusesADescriptorWhoseNameContainsTheIdentitySeparator() {
        // given
        SupplierProviderFactory factory = mock(SupplierProviderFactory.class);
        SupplierProviderDescriptor bad = mock(SupplierProviderDescriptor.class);
        when(bad.supplierInfo()).thenReturn(new SupplierInfo("Bad-Name", SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free()))));
        when(factory.availableProviders()).thenReturn(List.of(bad));

        // when / then
        assertThatThrownBy(() -> new SupplierRegistry(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bad-Name");
    }
}
