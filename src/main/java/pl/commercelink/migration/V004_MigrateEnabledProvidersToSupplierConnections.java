package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
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
import java.util.Map;
import java.util.Objects;

@ChangeUnit(id = "V004-migrate-enabled-providers-to-supplier-connections", order = "004", author = "commercelink")
@RequiredArgsConstructor
public class V004_MigrateEnabledProvidersToSupplierConnections {

    private final StoresRepository storesRepository;
    private final AmazonDynamoDB dynamoDB;

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
                List<StoreSupplierConnection> connections = new ArrayList<>();
                for (String name : readEnabledProviders(store.getStoreId())) {
                    connections.add(new StoreSupplierConnection(name, ConnectionMode.GLOBAL));
                }
                config.setSupplierConnections(connections);
            }

            storesRepository.save(store);
        }
    }

    private List<String> readEnabledProviders(String storeId) {
        GetItemRequest request = new GetItemRequest()
                .withTableName("Stores")
                .withKey(Map.of("storeId", new AttributeValue().withS(storeId)))
                .withProjectionExpression("fulfilment.enabledProviders");

        Map<String, AttributeValue> item = dynamoDB.getItem(request).getItem();
        if (item == null) {
            return List.of();
        }
        AttributeValue fulfilment = item.get("fulfilment");
        if (fulfilment == null || fulfilment.getM() == null) {
            return List.of();
        }
        AttributeValue enabledProviders = fulfilment.getM().get("enabledProviders");
        if (enabledProviders == null || enabledProviders.getL() == null) {
            return List.of();
        }
        return enabledProviders.getL().stream()
                .map(AttributeValue::getS)
                .filter(Objects::nonNull)
                .toList();
    }

    @RollbackExecution
    public void rollback() {
    }
}
