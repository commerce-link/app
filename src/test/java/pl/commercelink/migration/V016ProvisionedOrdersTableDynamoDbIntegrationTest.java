package pl.commercelink.migration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.orders.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V016 on an Orders table with provisioned capacity (a table created by hand may be one): the new index takes the
 * table's throughput instead of being rejected, which would stop the application from starting.
 */
@Testcontainers(disabledWithoutDocker = true)
class V016ProvisionedOrdersTableDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    @Test
    void addsTheIndexWithTheTablesThroughput() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        ProvisionedThroughput throughput = new ProvisionedThroughput(7L, 3L);
        CreateTableRequest request = new DynamoDBMapper(client).generateCreateTableRequest(Order.class)
                .withProvisionedThroughput(throughput);
        request.getGlobalSecondaryIndexes().forEach(index -> index.withProvisionedThroughput(throughput));
        client.createTable(request);

        new V016_AddStoreIdStatusIndex(client).execute();

        GlobalSecondaryIndexDescription index = client.describeTable("Orders").getTable().getGlobalSecondaryIndexes().stream()
                .filter(i -> V016_AddStoreIdStatusIndex.INDEX.equals(i.getIndexName())).findFirst().orElseThrow();
        assertThat(index.getProvisionedThroughput().getReadCapacityUnits()).isEqualTo(7L);
        assertThat(index.getProvisionedThroughput().getWriteCapacityUnits()).isEqualTo(3L);
    }
}
