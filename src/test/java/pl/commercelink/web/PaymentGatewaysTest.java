package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.PaymentOptionView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewaysTest {

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private MessageSource messageSource;

    @Mock
    private PaymentProviderDescriptor installedDescriptor;

    @Test
    void paymentOptionsListTheStoreGatewaysInOrderWithTheirDefault() {
        // given
        Store store = new Store();
        store.addPaymentIntegration("installed");
        store.addPaymentIntegration("missing");
        store.setDefaultPaymentIntegration("missing");
        when(paymentProviderFactory.getDescriptor("installed")).thenReturn(installedDescriptor);
        when(installedDescriptor.displayName()).thenReturn("Installed Gateway");

        // when
        var options = gateways().paymentOptions(store);

        // then
        assertThat(options).containsExactly(
                new PaymentOptionView("installed", "Installed Gateway", false),
                new PaymentOptionView("missing", "missing", true));
    }

    @Test
    void storeWithoutGatewaysHasNoPaymentOptions() {
        // when
        var options = gateways().paymentOptions(new Store());

        // then
        assertThat(options).isEmpty();
    }

    private PaymentGateways gateways() {
        return new PaymentGateways(paymentProviderFactory, messageSource, "https://api.example");
    }
}
