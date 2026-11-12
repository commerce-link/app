package pl.commercelink.testsupport;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import org.testcontainers.containers.GenericContainer;

/** DynamoDB Local in a Testcontainers container ({@link DynamoDbLocalImage}): its client and the tables of a test. */
public final class DynamoDbLocal {

    private DynamoDbLocal() {
    }

    public static AmazonDynamoDB clientOf(GenericContainer<?> container) {
        return AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + container.getHost() + ":" + container.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
    }

    /** The table of each entity, with its global secondary indexes, as the mapper describes it from the annotations. */
    public static void createTables(AmazonDynamoDB client, Class<?>... entities) {
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        ProvisionedThroughput throughput = new ProvisionedThroughput(1L, 1L);
        for (Class<?> entity : entities) {
            CreateTableRequest request = mapper.generateCreateTableRequest(entity).withProvisionedThroughput(throughput);
            if (request.getGlobalSecondaryIndexes() != null) {
                request.getGlobalSecondaryIndexes().forEach(index -> index.withProvisionedThroughput(throughput));
            }
            client.createTable(request);
        }
    }
}
