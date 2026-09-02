package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreControllerShippingWebhookUrlTest {

    @Mock
    private ShippingProviderFactory shippingProviderFactory;

    @InjectMocks
    private StoreController controller;

    private final Store store = new Store();

    @Test
    void buildsWebhookUrlFromApiDomain() {
        // given
        ReflectionTestUtils.setField(controller, "apiDomain", "https://api.example.test");

        // when
        String url = ReflectionTestUtils.invokeMethod(controller, "shippingWebhookUrl", "store-1", "furgonetka");

        // then
        assertThat(url).isEqualTo("https://api.example.test/Store/store-1/Webhooks/Shipping/furgonetka");
    }

    @Test
    void returnsNullForBlankProvider() {
        // given
        ReflectionTestUtils.setField(controller, "apiDomain", "https://api.example.test");

        // when
        String url = ReflectionTestUtils.invokeMethod(controller, "shippingWebhookUrl", "store-1", " ");

        // then
        assertThat(url).isNull();
    }

    @Test
    void returnsNullForNullProvider() {
        // given
        ReflectionTestUtils.setField(controller, "apiDomain", "https://api.example.test");

        // when
        String url = ReflectionTestUtils.invokeMethod(controller, "shippingWebhookUrl", "store-1", null);

        // then
        assertThat(url).isNull();
    }

    @Test
    void doesNotDoubleSlashWhenApiDomainHasTrailingSlash() {
        // given
        ReflectionTestUtils.setField(controller, "apiDomain", "https://api.example.test/");

        // when
        String url = ReflectionTestUtils.invokeMethod(controller, "shippingWebhookUrl", "store-1", "furgonetka");

        // then
        assertThat(url).isEqualTo("https://api.example.test/Store/store-1/Webhooks/Shipping/furgonetka");
    }

    @Test
    void webhookTokenMissingWhenProviderDeclaresTheFieldAndValueIsBlank() {
        // given
        ShippingProviderDescriptor descriptor = mock(ShippingProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(new ProviderField("webhookToken", "Webhook token", ProviderField.FieldType.PASSWORD, false, null)));
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(descriptor);
        when(shippingProviderFactory.loadConfiguration(store, "furgonetka")).thenReturn(Map.of("webhookToken", " "));

        // when
        boolean result = ReflectionTestUtils.invokeMethod(controller, "webhookTokenMissing", store, "furgonetka");

        // then
        assertTrue(result);
    }

    @Test
    void webhookTokenNotMissingWhenConfigured() {
        // given
        ShippingProviderDescriptor descriptor = mock(ShippingProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of(new ProviderField("webhookToken", "Webhook token", ProviderField.FieldType.PASSWORD, false, null)));
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(descriptor);
        when(shippingProviderFactory.loadConfiguration(store, "furgonetka")).thenReturn(Map.of("webhookToken", "secret"));

        // when
        boolean result = ReflectionTestUtils.invokeMethod(controller, "webhookTokenMissing", store, "furgonetka");

        // then
        assertFalse(result);
    }

    @Test
    void webhookTokenNotMissingWhenProviderHasNoSuchField() {
        // given
        ShippingProviderDescriptor descriptor = mock(ShippingProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(List.of());
        when(shippingProviderFactory.getDescriptor("furgonetka")).thenReturn(descriptor);

        // when
        boolean result = ReflectionTestUtils.invokeMethod(controller, "webhookTokenMissing", store, "furgonetka");

        // then
        assertFalse(result);
        verify(shippingProviderFactory, never()).loadConfiguration(store, "furgonetka");
    }

    @Test
    void webhookTokenNotMissingWhenNoProviderSelected() {
        // when
        boolean result = ReflectionTestUtils.invokeMethod(controller, "webhookTokenMissing", store, null);

        // then
        assertFalse(result);
    }
}
