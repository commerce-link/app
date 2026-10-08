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
import pl.commercelink.stores.BankAccount;
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
    void codAccountIsTheDefaultBankAccountAsHolderAndIban() {
        // given
        views = new ShippingIntegrationViews(messageSource, shippingProviderFactory);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("text");
        BankAccount account = new BankAccount();
        account.setAccountHolder("Sklep Sp. z o.o.");
        account.setIban("PL61109010140000071219812874");
        Store store = new Store();
        store.setStoreId("store-1");
        store.addBankAccount(account, true);
        ShipmentProposal proposal = ShipmentProposal.available("Method", null, null, DeliveryType.DOOR, List.of(), null, null);

        // when
        AllegroShippingView view = views.allegro(proposal, new Order("store-1"), store, Locale.ENGLISH);

        // then
        assertThat(view.codAccount()).isEqualTo("Sklep Sp. z o.o., PL61109010140000071219812874");
    }

    @Test
    void storeWithoutABankAccountHasNoCodAccount() {
        // given
        views = new ShippingIntegrationViews(messageSource, shippingProviderFactory);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("text");
        Store store = new Store();
        store.setStoreId("store-1");
        ShipmentProposal proposal = ShipmentProposal.available("Method", null, null, DeliveryType.DOOR, List.of(), null, null);

        // when
        AllegroShippingView view = views.allegro(proposal, new Order("store-1"), store, Locale.ENGLISH);

        // then
        assertThat(view.codAccount()).isNull();
        assertThat(view.carrierName()).isNull();
    }

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
}
