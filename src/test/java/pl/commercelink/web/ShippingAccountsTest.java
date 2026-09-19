package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.IntegrationSettingsForm;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingAccountsTest {

    private static final String PROVIDER = "furgonetka";

    @Mock
    private ShippingProviderFactory shippingProviderFactory;
    @Mock
    private ShippingProviderDescriptor descriptor;
    @Mock
    private ShippingProvider provider;

    private ShippingAccounts accounts;
    private Store store;

    @BeforeEach
    void storeUsingACourierWithWebhooks() {
        when(descriptor.displayName()).thenReturn("Furgonetka");
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("username", "API Username", FieldType.TEXT, true, null),
                new ProviderField("password", "API Password", FieldType.PASSWORD, true, null),
                new ProviderField("webhookToken", "Webhook token", FieldType.PASSWORD, false, null)));
        when(shippingProviderFactory.getDescriptor(PROVIDER)).thenReturn(descriptor);
        accounts = new ShippingAccounts(shippingProviderFactory, "https://api.example.test/");
        store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, PROVIDER);
    }

    private void storedSettings(Map<String, String> settings) {
        when(shippingProviderFactory.loadConfigurationForUI(store)).thenReturn(settings);
    }

    @Test
    void theAccountIsConfiguredOnlyWhenEveryRequiredSettingIsSaved() {
        // when
        storedSettings(Map.of("username", "sklep"));
        boolean withoutPassword = accounts.status(store).configured();
        storedSettings(Map.of("username", "sklep", "password", ""));
        boolean complete = accounts.status(store).configured();

        // then
        assertThat(withoutPassword).isFalse();
        assertThat(complete).isTrue();
    }

    @Test
    void anAccountWhoseAdapterIsGoneIsNamedButNotInstalled() {
        // given
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "gone");

        // when / then
        assertThat(accounts.status(store)).satisfies(status -> {
            assertThat(status.chosen()).isTrue();
            assertThat(status.installed()).isFalse();
            assertThat(status.displayName()).isEqualTo("gone");
        });
    }

    @Test
    void theWebhookAddressIsBuiltFromTheApiDomainForAProviderWithWebhooks() {
        // when / then
        assertThat(accounts.webhookUrl("store-1", PROVIDER))
                .isEqualTo("https://api.example.test/Store/store-1/Webhooks/Shipping/furgonetka");
        assertThat(accounts.webhookUrl("store-1", " ")).isNull();
        assertThat(accounts.webhookUrl("store-1", "unknown")).isNull();
    }

    @Test
    void aMissingWebhookTokenIsReadFromTheStoredSecretNotTheBlankedOne() {
        // when
        when(shippingProviderFactory.loadConfiguration(store, PROVIDER)).thenReturn(Map.of("username", "sklep"));
        boolean missing = accounts.webhookTokenMissing(store);
        when(shippingProviderFactory.loadConfiguration(store, PROVIDER)).thenReturn(Map.of("webhookToken", "secret"));
        boolean present = accounts.webhookTokenMissing(store);

        // then
        assertThat(missing).isTrue();
        assertThat(present).isFalse();
    }

    @Test
    void carriersAreAskedFromTheProviderOfAConfiguredAccount() {
        // given
        storedSettings(Map.of("username", "sklep", "password", ""));
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        when(provider.getAvailableCarriers()).thenReturn(List.of(new Carrier("1", "dpd", "DPD")));

        // when
        ShippingAccounts.CarrierLookup lookup = accounts.carriers(store);

        // then
        assertThat(lookup.failed()).isFalse();
        assertThat(lookup.carriers()).extracting(Carrier::displayName).containsExactly("DPD");
    }

    @Test
    void aFailedCallIsReportedWithTheProvidersMessageInsteadOfThrown() {
        // given
        storedSettings(Map.of("username", "sklep", "password", ""));
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        when(provider.getAvailableCarriers()).thenThrow(new IllegalStateException("HTTP 401: " + "x".repeat(500)));

        // when
        ShippingAccounts.CarrierLookup lookup = accounts.carriers(store);

        // then
        assertThat(lookup.failed()).isTrue();
        assertThat(lookup.error()).startsWith("HTTP 401").hasSizeLessThanOrEqualTo(300);
        assertThat(lookup.carriers()).isEmpty();
    }

    @Test
    void anAccountWithoutItsSettingsIsNotAskedForCarriers() {
        // given
        storedSettings(Map.of());

        // when
        ShippingAccounts.CarrierLookup lookup = accounts.carriers(store);

        // then
        assertThat(lookup.failed()).isFalse();
        assertThat(lookup.carriers()).isEmpty();
    }

    @Test
    void aRequiredAddressWithAFullExampleAddressCountsAsSetWhenTheSecretLacksIt() {
        // given
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("apiUrl", "API URL", FieldType.TEXT, true, "https://api.furgonetka.pl"),
                new ProviderField("username", "API Username", FieldType.TEXT, true, null)));
        storedSettings(Map.of("username", "sklep"));

        // when / then
        assertThat(accounts.status(store).configured()).isTrue();
    }

    @Test
    void anEmptyAddressFieldTakesTheAdaptersExampleAddressButAnExampleOfAnotherValueStaysAnExample() {
        // given
        when(descriptor.name()).thenReturn(PROVIDER);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("apiUrl", "API URL", FieldType.TEXT, true, "https://api.furgonetka.pl"),
                new ProviderField("username", "API Username", FieldType.TEXT, true, "sklep@example.pl"),
                new ProviderField("optionalUrl", "Optional URL", FieldType.URL, false, "https://example.pl")));
        when(shippingProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.setProviderName(PROVIDER);
        form.setSettings(new java.util.HashMap<>(Map.of("furgonetka.apiUrl", " ")));

        // when
        accounts.fillDefaults(form);

        // then
        assertThat(form.getSettings()).containsEntry("furgonetka.apiUrl", "https://api.furgonetka.pl")
                .doesNotContainKey("furgonetka.username").doesNotContainKey("furgonetka.optionalUrl");
    }
}
