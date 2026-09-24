package pl.commercelink.web.dtos;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.products.Product;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-3 / R-D3-19: what DynamoDB holds after a product from the bulk-add review is saved. The mapper reads the EAN and
 * the manufacturer code through the getters of {@link Product}, but {@code unifyEan} strips leading zeros only and
 * never trims, so an EAN posted with spaces around it was stored as typed; the stored item is the evidence.
 */
@Testcontainers(disabledWithoutDocker = true)
class BulkAddedProductDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static DynamoDBMapper mapper;

    @BeforeAll
    static void createTheProductsTable() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        ProvisionedThroughput throughput = new ProvisionedThroughput(1L, 1L);
        CreateTableRequest request = mapper.generateCreateTableRequest(Product.class).withProvisionedThroughput(throughput);
        if (request.getGlobalSecondaryIndexes() != null) {
            request.getGlobalSecondaryIndexes().forEach(index -> index.withProvisionedThroughput(throughput));
        }
        client.createTable(request);
    }

    @Test
    void theReviewedRowIsStoredWithItsIdentifiersNormalised() {
        // given
        ProductsBulkAddForm.Row row = new ProductsBulkAddForm.Row();
        row.setName("MSI RTX 5070");
        row.setEan(" 05900000000101 ");
        row.setManufacturerCode(" gv-n5080 oc ");
        row.setPricingGroup("Default");
        Product product = row.toProduct("cat-1");

        // when
        mapper.save(product);

        // then
        Map<String, AttributeValue> item = client.getItem("Products", Map.of(
                "categoryId", new AttributeValue("cat-1"),
                "productId", new AttributeValue(product.getProductId()))).getItem();
        assertThat(item.get("ean").getS()).isEqualTo("5900000000101");
        assertThat(item.get("mfn").getS()).isEqualTo("GV-N5080OC");
    }
}
