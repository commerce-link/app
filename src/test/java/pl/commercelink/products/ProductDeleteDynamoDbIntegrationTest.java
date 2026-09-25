package pl.commercelink.products;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The deletions of the catalog screens against DynamoDB Local: a product read by the page, then saved by another
 * request, then deleted with the copy the page read. A plain delete of the versioned item is conditional on that
 * copy's version and fails (the 500 of a bulk or single delete racing a save); the catalog's delete does not ask.
 */
@Testcontainers(disabledWithoutDocker = true)
class ProductDeleteDynamoDbIntegrationTest {

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

    /** The copy the page read, after another request saved the product once more. */
    private static Product readThenSavedMeanwhile(String productId) {
        Product product = new Product("cat-delete", null, "5900000000008", "MFN-1", "AMD", null, "Ryzen", "Default");
        product.setProductId(productId);
        products.save(product);
        Product readByThePage = products.findByProductId("cat-delete", productId);
        Product otherRequest = products.findByProductId("cat-delete", productId);
        otherRequest.setName("Renamed meanwhile");
        products.save(otherRequest);
        return readByThePage;
    }

    @Test
    void aPlainDeleteOfACopySavedMeanwhileFails() {
        // given
        Product stale = readThenSavedMeanwhile("p-plain");

        // when / then
        assertThatThrownBy(() -> products.delete(stale)).isInstanceOf(ConditionalCheckFailedException.class);
        assertThat(products.findByProductId("cat-delete", "p-plain")).isNotNull();
    }

    @Test
    void theCatalogDeleteRemovesACopySavedMeanwhile() {
        // given
        Product stale = readThenSavedMeanwhile("p-catalog");

        // when
        products.deleteWhateverItsVersion(stale);

        // then
        assertThat(products.findByProductId("cat-delete", "p-catalog")).isNull();
    }

    @Test
    void theCatalogDeleteOfAProductAlreadyGoneIsNoError() {
        // given
        Product stale = readThenSavedMeanwhile("p-gone");
        products.deleteWhateverItsVersion(stale);

        // when / then
        products.deleteWhateverItsVersion(stale);
        assertThat(products.findByProductId("cat-delete", "p-gone")).isNull();
    }
}
