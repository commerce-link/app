package pl.commercelink.starter.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationConfigProviderTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreActivity storeActivity;

    @InjectMocks
    private DefaultNotificationConfigProvider provider;

    @BeforeEach
    void storesAreActive() {
        lenient().when(storeActivity.isActive(any(Store.class))).thenReturn(true);
    }

    private Store store(String senderName, String replyToEmail, String companyEmail) {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Sklep Demo");
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        configuration.setSenderName(senderName);
        configuration.setReplyToEmail(replyToEmail);
        store.setClientNotificationsConfiguration(configuration);
        BillingDetails billing = new BillingDetails();
        billing.setEmail(companyEmail);
        store.setBillingDetails(billing);
        when(storesRepository.findById("store-1")).thenReturn(store);
        return store;
    }

    @Test
    void theConfiguredSenderNameIsShownToCustomers() {
        // given
        store("Obsługa Sklepu Demo", null, null);

        // when / then
        assertThat(provider.settings("store-1").senderName()).isEqualTo("Obsługa Sklepu Demo");
    }

    @Test
    void aBlankSenderNameFallsBackToTheStoreName() {
        // given
        store("  ", null, null);

        // when / then
        assertThat(provider.settings("store-1").senderName()).isEqualTo("Sklep Demo");
    }

    @Test
    void theConfiguredReplyToAddressReceivesCustomerReplies() {
        // given
        store(null, "kontakt@sklep-demo.pl", "biuro@sklep-demo.pl");

        // when / then
        assertThat(provider.settings("store-1").replyToEmail()).isEqualTo("kontakt@sklep-demo.pl");
    }

    @Test
    void aBlankReplyToAddressFallsBackToTheCompanyEmail() {
        // given
        store(null, "", "biuro@sklep-demo.pl");

        // when / then
        assertThat(provider.settings("store-1").replyToEmail()).isEqualTo("biuro@sklep-demo.pl");
    }

    @Test
    void anUnknownStoreHasNoSettings() {
        // when / then
        assertThat(provider.settings("missing")).isNull();
    }

    @Test
    void thereIsNoReplyToAddressWhenTheStoreHasNeitherOne() {
        // given
        store(null, null, null);

        // when / then
        assertThat(provider.settings("store-1").replyToEmail()).isNull();
    }

    @Test
    void inactiveStoreHasNoSettingsSoItSendsNothing() {
        // given
        Store store = store("Obsługa Sklepu Demo", null, null);
        when(storeActivity.isActive(store)).thenReturn(false);

        // when / then
        assertThat(provider.settings("store-1")).isNull();
    }

    @Test
    void missingStoreHasNoSettings() {
        // given
        when(storesRepository.findById("store-2")).thenReturn(null);

        // when / then
        assertThat(provider.settings("store-2")).isNull();
    }
}
