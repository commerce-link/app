package pl.commercelink.products;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * D-I7: the catalog page counts the products of each manual category and the ones whose label is off the list from a
 * query that projects the label only, against DynamoDB Local: every product is counted (one entry each, also without a
 * label), every page of the query is followed, and nothing but the label comes back.
 */
@Testcontainers(disabledWithoutDocker = true)
class ProductLabelsDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static ProductRepository products;

    @BeforeAll
    static void createTheProductsTable() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        ProvisionedThroughput throughput = new ProvisionedThroughput(1L, 1L);
        CreateTableRequest request = new DynamoDBMapper(client).generateCreateTableRequest(Product.class)
                .withProvisionedThroughput(throughput);
        if (request.getGlobalSecondaryIndexes() != null) {
            request.getGlobalSecondaryIndexes().forEach(index -> index.withProvisionedThroughput(throughput));
        }
        client.createTable(request);
        products = new ProductRepository(client);
    }

    private static void save(String categoryId, String... labels) {
        for (String label : labels) {
            products.save(new Product(categoryId, "pim", "5900000000008", "MFN", "Brand", label, "Name", "Default"));
        }
    }

    @Test
    void answersOneLabelPerProductOfTheCategoryOnly() {
        // given
        save("cat-labels", "RTX 5060", "RTX 5060", "RTX 4060", null);
        save("cat-other", "RTX 5090");

        // when
        List<String> labels = products.labelsOf("cat-labels");

        // then
        assertThat(labels).containsExactlyInAnyOrder("RTX 5060", "RTX 5060", "RTX 4060", null);
    }

    @Test
    void followsEveryPageOfTheQuery() {
        // given
        save("cat-pages", "A", "B", "C", "D", "E");
        AmazonDynamoDB spy = Mockito.mock(AmazonDynamoDB.class, AdditionalAnswers.delegatesTo(client));

        // when: two items a page, so three pages
        List<String> labels = new ProductRepository(spy).labelsOf("cat-pages", 2);

        // then
        assertThat(labels).containsExactlyInAnyOrder("A", "B", "C", "D", "E");
        verify(spy, times(3)).query(Mockito.any(QueryRequest.class));
    }

    @Test
    void readsTheLabelOnly() {
        // given
        save("cat-projection", "RTX 5060");
        AmazonDynamoDB spy = Mockito.mock(AmazonDynamoDB.class, AdditionalAnswers.delegatesTo(client));

        // when
        List<String> labels = new ProductRepository(spy).labelsOf("cat-projection");

        // then
        ArgumentCaptor<QueryRequest> request = ArgumentCaptor.forClass(QueryRequest.class);
        verify(spy, atLeast(1)).query(request.capture());
        assertThat(request.getValue().getProjectionExpression()).isEqualTo("label");
        assertThat(labels).containsExactly("RTX 5060");
    }

    @Test
    void anEmptyCategoryHasNoLabels() {
        // when / then
        assertThat(products.labelsOf("cat-empty")).isEmpty();
    }
}
