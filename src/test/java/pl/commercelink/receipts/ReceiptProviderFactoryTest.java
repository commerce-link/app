package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.provider.ProviderConfigurationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReceiptProviderFactoryTest {

    @Test
    void discoversReceiptDescriptorsThroughServiceLoader() {
        ReceiptProviderFactory factory = new ReceiptProviderFactory(mock(ProviderConfigurationManager.class),
                ProviderCallLimiter.unlimited());

        assertThat(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).isNotNull();
    }
}
