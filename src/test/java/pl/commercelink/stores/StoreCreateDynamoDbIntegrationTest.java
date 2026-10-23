package pl.commercelink.stores;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * The create and copy forms hand out the new store's id with the page; this pins why a resent or forged id can never
 * overwrite a store: Store is versioned, so saving a new one (version null) under a taken id is refused by DynamoDB.
 */
@Testcontainers(disabledWithoutDocker = true)
class StoreCreateDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    static StoresRepository stores;

    @BeforeAll
    static void createTable() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        client.createTable(new DynamoDBMapper(client).generateCreateTableRequest(Store.class)
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
        stores = new StoresRepository(client, mock(FileStorage.class), "stores");
    }

    @Test
    void newStoreUnderATakenIdIsRefusedAndTheFirstOneStays() {
        // given
        stores.save(store("Pierwszy"));

        // when / then
        assertThrows(ConditionalCheckFailedException.class, () -> stores.save(store("Drugi")));
        assertEquals("Pierwszy", stores.findById("taken00001").getName());
    }

    private static Store store(String name) {
        Store store = new Store();
        store.setStoreId("taken00001");
        store.setName(name);
        return store;
    }
}
