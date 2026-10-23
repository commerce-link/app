package pl.commercelink.orders.rma;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** findByRmaId reads one partition of RMAItems by its key instead of scanning the whole table. */
@Testcontainers(disabledWithoutDocker = true)
class RMAItemsRepositoryFindByRmaIdDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    RMAItemsRepository items;

    @BeforeEach
    void createTable() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        if (client.listTables().getTableNames().contains("RMAItems")) {
            client.deleteTable("RMAItems");
        }
        client.createTable(mapper.generateCreateTableRequest(RMAItem.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        items = new RMAItemsRepository(client);
    }

    private void save(String rmaId, String rmaItemId, String name) {
        RMAItem item = new RMAItem();
        item.setRmaId(rmaId);
        item.setRmaItemId(rmaItemId);
        item.setName(name);
        items.save(item);
    }

    @Test
    void findByRmaIdReturnsEveryItemOfTheRmaAndNoneOfAnother() {
        // given
        save("rma-1", "b", "Kabel");
        save("rma-1", "a", "Laptop");
        save("rma-1", "c", "Mysz");
        save("rma-2", "a", "Monitor");
        save("rma-10", "x", "Klawiatura");

        // when
        List<RMAItem> found = items.findByRmaId("rma-1");

        // then
        assertThat(found).extracting(RMAItem::getRmaItemId).containsExactly("a", "b", "c");
        assertThat(found).extracting(RMAItem::getName).containsExactly("Laptop", "Kabel", "Mysz");
        assertThat(found).allMatch(item -> "rma-1".equals(item.getRmaId()));
    }

    @Test
    void findByRmaIdOfAnRmaWithoutItemsIsAnEmptyListTheCallerMayChange() {
        // given
        save("rma-2", "a", "Monitor");

        // when
        List<RMAItem> found = items.findByRmaId("rma-1");
        found.add(new RMAItem());

        // then
        assertThat(found).hasSize(1);
    }
}
