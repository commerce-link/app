package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;

@ChangeUnit(id = "V004-migrate-enabled-providers-to-supplier-connections", order = "004", author = "commercelink")
@RequiredArgsConstructor
public class V004_MigrateEnabledProvidersToSupplierConnections {

    private final StoresRepository storesRepository;

    @Execution
    public void migrate() {
        for (Store store : storesRepository.findAll()) {
            FulfilmentConfiguration config = store.getFulfilmentConfiguration();
            if (config == null) {
                continue;
            }

            config.setCanUseGlobalSuppliers(true);

            boolean hasConnections = config.getSupplierConnections() != null && !config.getSupplierConnections().isEmpty();
            if (!hasConnections) {
                List<String> enabled = config.getEnabledProviders();
                List<StoreSupplierConnection> connections = new ArrayList<>();
                if (enabled != null) {
                    for (String name : enabled) {
                        connections.add(new StoreSupplierConnection(name, ConnectionMode.GLOBAL));
                    }
                }
                config.setSupplierConnections(connections);
            }

            storesRepository.save(store);
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
