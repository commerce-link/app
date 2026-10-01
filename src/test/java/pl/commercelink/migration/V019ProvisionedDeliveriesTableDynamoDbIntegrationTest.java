package pl.commercelink.migration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.inventory.deliveries.Delivery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V019 on a Deliveries table with provisioned capacity (a table created by hand may be one): the new index takes the
 * table's throughput instead of being rejected, which would stop the application from starting.
 */
@Testcontainers(disabledWithoutDocker = true)
class V019ProvisionedDeliveriesTableDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    @Test
    void addsTheIndexWithTheTablesThroughput() {
        // given
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        CreateTableRequest request = new DynamoDBMapper(client).generateCreateTableRequest(Delivery.class)
                .withProvisionedThroughput(new ProvisionedThroughput(7L, 3L));
        client.createTable(request);

        // when
        new V019_AddDeliveriesListKeyIndex(client).execute();

        // then
        GlobalSecondaryIndexDescription index = client.describeTable("Deliveries").getTable().getGlobalSecondaryIndexes().stream()
                .filter(i -> V019_AddDeliveriesListKeyIndex.INDEX.equals(i.getIndexName())).findFirst().orElseThrow();
        assertThat(index.getProvisionedThroughput().getReadCapacityUnits()).isEqualTo(7L);
        assertThat(index.getProvisionedThroughput().getWriteCapacityUnits()).isEqualTo(3L);
        assertThat(index.getProjection().getProjectionType()).isEqualTo(ProjectionType.INCLUDE.toString());
        assertThat(index.getProjection().getNonKeyAttributes()).contains("tracking");
    }
}
