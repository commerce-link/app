package pl.commercelink.migration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.orders.Order;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V023 on an Orders table shaped like production before it: the other indexes, no DiscountCodeIndex. */
@Testcontainers(disabledWithoutDocker = true)
class V023AddDiscountCodeIndexDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    AmazonDynamoDB client;

    @BeforeEach
    void connect() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        if (client.listTables().getTableNames().contains("Orders")) {
            client.deleteTable("Orders");
        }
    }

    @AfterEach
    void letTheIndexBuildBeforeTheNextTestDropsTheTable() {
        awaitIndexesActive();
    }

    @Test
    void addsTheIndexByCodeAndOrderDayWithTheListedAttributes() {
        // given
        client.createTable(ordersTableBefore().withBillingMode(BillingMode.PAY_PER_REQUEST));

        // when
        migration().execute();

        // then
        GlobalSecondaryIndexDescription index = discountCodeIndex();
        assertThat(index.getKeySchema()).containsExactly(
                new KeySchemaElement("discountCode", KeyType.HASH), new KeySchemaElement("orderedAt", KeyType.RANGE));
        assertThat(index.getProjection().getProjectionType()).isEqualTo("INCLUDE");
        assertThat(index.getProjection().getNonKeyAttributes())
                .containsExactlyInAnyOrder("email", "totalPrice", "discountAmount", "status");
    }

    @Test
    void anExistingIndexIsLeftAlone() {
        // given
        client.createTable(ordersTableBefore().withBillingMode(BillingMode.PAY_PER_REQUEST));
        migration().execute();
        int indexes = client.describeTable("Orders").getTable().getGlobalSecondaryIndexes().size();

        // when
        migration().execute();

        // then
        assertThat(client.describeTable("Orders").getTable().getGlobalSecondaryIndexes()).hasSize(indexes);
    }

    @Test
    void aProvisionedTableGetsAnIndexWithItsOwnThroughput() {
        // given
        ProvisionedThroughput throughput = new ProvisionedThroughput(7L, 3L);
        CreateTableRequest request = ordersTableBefore().withProvisionedThroughput(throughput);
        request.getGlobalSecondaryIndexes().forEach(index -> index.withProvisionedThroughput(throughput));
        client.createTable(request);

        // when
        migration().execute();

        // then
        ProvisionedThroughputDescription capacity = discountCodeIndex().getProvisionedThroughput();
        assertThat(capacity.getReadCapacityUnits()).isEqualTo(7L);
        assertThat(capacity.getWriteCapacityUnits()).isEqualTo(3L);
    }

    private V023_AddDiscountCodeIndex migration() {
        return new V023_AddDiscountCodeIndex(client, millis -> { }, Duration.ofSeconds(5));
    }

    private CreateTableRequest ordersTableBefore() {
        CreateTableRequest request = new DynamoDBMapper(client).generateCreateTableRequest(Order.class);
        request.getGlobalSecondaryIndexes().removeIf(index -> V023_AddDiscountCodeIndex.INDEX.equals(index.getIndexName()));
        request.getAttributeDefinitions().removeIf(attribute -> "discountCode".equals(attribute.getAttributeName()));
        request.getGlobalSecondaryIndexes().forEach(index -> index.withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY)));
        return request;
    }

    private GlobalSecondaryIndexDescription discountCodeIndex() {
        return client.describeTable("Orders").getTable().getGlobalSecondaryIndexes().stream()
                .filter(index -> V023_AddDiscountCodeIndex.INDEX.equals(index.getIndexName()))
                .findFirst().orElseThrow();
    }

    // V023 creates the index without waiting for it; deleting the table while dynamodb-local builds it can fail
    private void awaitIndexesActive() {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        while (!indexesActive()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Orders indexes still building: "
                        + client.describeTable("Orders").getTable().getGlobalSecondaryIndexes());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    // dynamodb-local reports no status at all for an index that is ready
    private boolean indexesActive() {
        if (!client.listTables().getTableNames().contains("Orders")) {
            return true;
        }
        List<GlobalSecondaryIndexDescription> indexes = client.describeTable("Orders").getTable().getGlobalSecondaryIndexes();
        return indexes == null || indexes.stream()
                .allMatch(index -> index.getIndexStatus() == null || "ACTIVE".equals(index.getIndexStatus()));
    }
}
