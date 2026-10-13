package pl.commercelink.warehouse.builtin;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** The store lists read one store's partition (Query on storeId), never the whole table of every store (Scan). */
@Testcontainers(disabledWithoutDocker = true)
class WarehouseRepositoryDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static WarehouseRepository repository;

    @BeforeAll
    static void createTheTableWithTwoStores() {
        client = spy(AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build());
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        client.createTable(mapper.generateCreateTableRequest(WarehouseItem.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        mapper.save(item("store-1", "s1-destroyed-ssd", "SSD", FulfilmentStatus.Destroyed));
        mapper.save(item("store-1", "s1-destroyed-ram", "RAM", FulfilmentStatus.Destroyed));
        mapper.save(item("store-1", "s1-delivered-ssd", "SSD", FulfilmentStatus.Delivered));
        mapper.save(item("store-1", "s1-ordered-gpu", "GPU", FulfilmentStatus.Ordered));
        mapper.save(item("store-1", "s1-allocation", null, FulfilmentStatus.Allocation));
        mapper.save(item("store-2", "s2-destroyed-ssd", "SSD", FulfilmentStatus.Destroyed));
        mapper.save(item("store-2", "s2-delivered-ssd", "SSD", FulfilmentStatus.Delivered));
        repository = new WarehouseRepository(client);
    }

    @BeforeEach
    void forgetTheSetupCalls() {
        clearInvocations(client);
    }

    static WarehouseItem item(String storeId, String id, String category, FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem(storeId, "d-1", category, id, "880", "MFN-" + id, 10.0, 1);
        item.setItemId(id);
        item.setStatus(status);
        return item;
    }

    private static List<String> ids(List<WarehouseItem> items) {
        return items.stream().map(WarehouseItem::getItemId).sorted().toList();
    }

    private static void readOnlyTheStorePartition() {
        verify(client, never()).scan(any(ScanRequest.class));
        verify(client, atLeastOnce()).query(argThat((QueryRequest q) -> q.getTableName().equals("WarehouseItems")
                && q.getKeyConditions() != null && q.getKeyConditions().containsKey("storeId")
                && "store-1".equals(q.getKeyConditions().get("storeId").getAttributeValueList().get(0).getS())));
    }

    @Test
    void destroyedArchiveReadsOnlyTheStoresDestroyedItems() {
        // when
        List<WarehouseItem> destroyed = repository.findAll("store-1", FulfilmentStatus.Destroyed);

        // then
        assertThat(ids(destroyed)).containsExactly("s1-destroyed-ram", "s1-destroyed-ssd");
        readOnlyTheStorePartition();
    }

    @Test
    void listWithoutFiltersReadsTheWholeStore() {
        // when
        List<WarehouseItem> all = repository.findAllFiltered("store-1", null, null);

        // then
        assertThat(ids(all)).containsExactly("s1-allocation", "s1-delivered-ssd", "s1-destroyed-ram", "s1-destroyed-ssd", "s1-ordered-gpu");
        readOnlyTheStorePartition();
    }

    @Test
    void listWithEmptyFiltersReadsTheWholeStore() {
        // when
        List<WarehouseItem> all = repository.findAllFiltered("store-1", List.of(), List.of());

        // then
        assertThat(all).hasSize(5);
        readOnlyTheStorePartition();
    }

    @Test
    void listFiltersByCategoriesAndStatusesWithinTheStore() {
        // when
        List<WarehouseItem> shown = repository.findAllFiltered("store-1", List.of("SSD", "GPU"),
                List.of(FulfilmentStatus.Delivered, FulfilmentStatus.Ordered));

        // then
        assertThat(ids(shown)).containsExactly("s1-delivered-ssd", "s1-ordered-gpu");
        readOnlyTheStorePartition();
    }

    @Test
    void listFiltersByStatusesOnly() {
        // when
        List<WarehouseItem> shown = repository.findAllFiltered("store-1", null, List.of(FulfilmentStatus.Allocation));

        // then
        assertThat(ids(shown)).containsExactly("s1-allocation");
        readOnlyTheStorePartition();
    }
}
