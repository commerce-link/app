package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StoreControllerShippingWebhookUrlTest {

    @InjectMocks
    private StoreController controller;

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
}
