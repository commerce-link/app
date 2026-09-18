package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoicingSystemsTest {

    private static final String SYSTEM = "fakturownia";

    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private InvoicingProviderDescriptor descriptor;

    private InvoicingSystems systems;
    private Store store;

    @BeforeEach
    void storeUsingASystemWithTwoRequiredSettings() {
        when(descriptor.displayName()).thenReturn("Fakturownia");
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("domain", "Domena", FieldType.TEXT, true, null),
                new ProviderField("apiToken", "Token API", FieldType.PASSWORD, true, null),
                new ProviderField("department", "Dział", FieldType.TEXT, false, null)));
        when(invoicingProviderFactory.getDescriptor(SYSTEM)).thenReturn(descriptor);
        systems = new InvoicingSystems(invoicingProviderFactory);
        store = new Store();
        store.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, SYSTEM);
    }

    /** "Connected" used to be shown for any provider name; configured means the required settings are saved. */
    @Test
    void theSystemIsConfiguredOnlyWhenEveryRequiredSettingIsSaved() {
        // when
        when(invoicingProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of("domain", "firma.fakturownia.pl"));
        IntegrationStatus withoutSecret = systems.status(store);
        when(invoicingProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of("domain", "firma.fakturownia.pl", "apiToken", ""));
        IntegrationStatus complete = systems.status(store);

        // then
        assertThat(withoutSecret).isEqualTo(new IntegrationStatus(SYSTEM, "Fakturownia", true, false));
        assertThat(complete).isEqualTo(new IntegrationStatus(SYSTEM, "Fakturownia", true, true));
    }

    @Test
    void aSystemWhoseAdapterIsGoneIsNotInstalledAndNoSystemIsNone() {
        // given
        Store retired = new Store();
        retired.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, "retired");

        // when / then
        assertThat(systems.status(retired)).isEqualTo(new IntegrationStatus("retired", "retired", false, false));
        assertThat(systems.status(new Store())).isEqualTo(IntegrationStatus.none());
    }

    @Test
    void storedSecretsCountOnlyForTheCurrentSystem() {
        // given
        when(invoicingProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of("apiToken", ""));

        // when / then
        assertThat(systems.storedSecretKeys(store, SYSTEM)).containsExactly("apiToken");
        assertThat(systems.storedSecretKeys(store, "another")).isEmpty();
    }
}
