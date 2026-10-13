package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.orders.Order;
import pl.commercelink.shipping.api.DeliveryType;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingIntegrationViewsTest {

    @Mock private MessageSource messageSource;
    @Mock private ShippingProviderFactory shippingProviderFactory;

    private ShippingIntegrationViews views;

    @Test
    void errorsOnOneFieldAreJoined() {
        // given
        views = new ShippingIntegrationViews(messageSource, shippingProviderFactory);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));

        // when
        Map<String, String> errors = views.errors(List.of(
                new AllegroShipmentFormCheck.Problem("parcel", "a", new Object[0]),
                new AllegroShipmentFormCheck.Problem("parcel", "b", new Object[0])), Locale.ENGLISH);

        // then
        assertThat(errors).containsEntry("parcel", "a b");
    }

    @Test
    void theAllegroOrderWarningNamesTheAccountInCorrectPolish() {
        // given
        org.springframework.context.support.ResourceBundleMessageSource messages =
                new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");

        // when
        String warning = messages.getMessage("shipping.integration.warning.allegroOrder",
                new Object[]{"Allegro One Box", "Furgonetka"}, Locale.forLanguageTag("pl"));

        // then
        assertThat(warning).contains("z konta integracji Furgonetka");
    }
}
