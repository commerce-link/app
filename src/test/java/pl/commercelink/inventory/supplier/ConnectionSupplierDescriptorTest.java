package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionSupplierDescriptorTest {

    private final SupplierProviderDescriptor stub = new StubSupplierDescriptor();

    @Test
    void reportsTheIdentityAsSupplierNameAndKeepsTheTypeMetadata() {
        // given / when
        ConnectionSupplierDescriptor descriptor = new ConnectionSupplierDescriptor("Stub-k7f3a9c2", stub);

        // then
        assertThat(descriptor.name()).isEqualTo("Stub-k7f3a9c2");
        assertThat(descriptor.supplierInfo().name()).isEqualTo("Stub-k7f3a9c2");
        assertThat(descriptor.supplierInfo().type()).isEqualTo(StubSupplierDescriptor.INFO.type());
        assertThat(descriptor.supplierInfo().shippingPolicy()).isEqualTo(StubSupplierDescriptor.INFO.shippingPolicy());
    }

    @Test
    void delegatesFeedFormatConfigurationAndProviderCreation() throws Exception {
        // given
        ConnectionSupplierDescriptor descriptor = new ConnectionSupplierDescriptor("Stub-k7f3a9c2", stub);

        // when / then
        assertThat(descriptor.feedFormat()).isInstanceOf(pl.commercelink.inventory.supplier.api.FeedFormat.Csv.class);
        assertThat(descriptor.configurationFields()).isEqualTo(stub.configurationFields());
        assertThat(new String(descriptor.create(Map.of("url", "x")).download().orElseThrow().data())).isEqualTo("x");
        assertThat(descriptor.authConfig()).isEqualTo(stub.authConfig());
    }

    @Test
    void isTransparentForALegacyIdentityEqualToTheType() {
        // given / when
        ConnectionSupplierDescriptor descriptor = new ConnectionSupplierDescriptor("Stub", stub);

        // then
        assertThat(descriptor.supplierInfo()).isEqualTo(StubSupplierDescriptor.INFO);
    }
}
