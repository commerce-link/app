package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreSupplierConnectionServiceManualTest {

    @Mock SupplierRegistry supplierRegistry;
    @Mock SupplierProviderFactory supplierProviderFactory;
    @Mock ProviderConfigurationManager configurationManager;
    @Mock SupplierConnectionValidator validator;
    @Mock StoreSupplierConnectionPersister persister;
    @InjectMocks StoreSupplierConnectionService service;

    @Test
    void manualConnectionsSurviveStaticFormRebuild() {
        // given
        Store existing = new Store();
        FulfilmentConfiguration existingConfig = new FulfilmentConfiguration();
        existingConfig.setCanUseGlobalSuppliers(true);
        existingConfig.setSupplierConnections(List.of(
                new StoreSupplierConnection("manual:H1", ConnectionMode.MANUAL, true, true)));
        existing.setFulfilmentConfiguration(existingConfig);

        FulfilmentConfiguration submitted = new FulfilmentConfiguration();
        submitted.setCanUseGlobalSuppliers(true);
        List<SupplierSelectionForm> selections = List.of(
                new SupplierSelectionForm("Acme", true, ConnectionMode.GLOBAL, true, true));

        when(validator.validate(anyBoolean(), any(), any(), any(), any())).thenReturn(List.of());
        when(persister.persist(any(), any(), any())).thenReturn(StoreSupplierConnectionPersister.PersistOutcome.success(java.util.Set.of(), java.util.Set.of()));

        // when
        service.apply(existing, submitted, selections, java.util.Map.of(), true);

        // then
        List<String> names = submitted.getSupplierConnections().stream()
                .map(StoreSupplierConnection::getSupplierName).toList();
        assertTrue(names.contains("manual:H1"));
        assertTrue(names.contains("Acme"));
    }
}
