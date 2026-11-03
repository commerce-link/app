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
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A category larger than one query page (1 MB) is read whole, against DynamoDB Local: the offer links decide "not in
 * the catalog" and "one product per EAN" from these products, so a product past the first page must not go missing.
 */
@Testcontainers(disabledWithoutDocker = true)
class ProductFindAllDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

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

    private static void save(String categoryId, String... manufacturerCodes) {
        for (String manufacturerCode : manufacturerCodes) {
            products.save(new Product(categoryId, null, "5900000000008", manufacturerCode, "Brand", "Label", "Name",
                    "Default"));
        }
    }

    @Test
    void findAllReadsEveryPageOfTheCategory() {
        // given
        save("cat-find-all", "MFN-A", "MFN-B", "MFN-C", "MFN-D", "MFN-E");
        save("cat-find-all-other", "MFN-X");
        AmazonDynamoDB spy = Mockito.mock(AmazonDynamoDB.class, AdditionalAnswers.delegatesTo(client));

        // when: two items a page, so three pages
        List<Product> found = new ProductRepository(spy).findAll("cat-find-all", 2);

        // then
        assertThat(found).extracting(Product::getManufacturerCode)
                .containsExactlyInAnyOrder("MFN-A", "MFN-B", "MFN-C", "MFN-D", "MFN-E");
        assertThat(found).allSatisfy(product -> assertThat(product.getCategoryId()).isEqualTo("cat-find-all"));
        verify(spy, times(3)).query(Mockito.any(QueryRequest.class));
    }

    @Test
    void findAllOfACatalogReadsEveryProductOfEachCategory() {
        // given
        save("cat-catalog-1", "MFN-1", "MFN-2");
        save("cat-catalog-2", "MFN-3");
        ProductCatalog catalog = new ProductCatalog();
        CategoryDefinition first = new CategoryDefinition();
        first.setCategoryId("cat-catalog-1");
        CategoryDefinition second = new CategoryDefinition();
        second.setCategoryId("cat-catalog-2");
        catalog.setCategories(List.of(first, second));

        // when
        List<Product> found = products.findAll(catalog);

        // then
        assertThat(found).extracting(Product::getManufacturerCode).containsExactlyInAnyOrder("MFN-1", "MFN-2", "MFN-3");
    }
}
