package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.testsupport.DynamoDbLocal;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A category larger than one query page (1 MB) is read whole, against DynamoDB Local: the offer links decide "not in
 * the catalog" and "one product per EAN" from these products, and the price list and the marketplace export of a manual
 * category publish them, so a product past the first page must not go missing.
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
        client = DynamoDbLocal.clientOf(DYNAMODB);
        DynamoDbLocal.createTables(client, Product.class);
        products = new ProductRepository(client);
    }

    private static void save(String categoryId, String... manufacturerCodes) {
        for (String manufacturerCode : manufacturerCodes) {
            products.save(new Product(categoryId, null, "5900000000008", manufacturerCode, "Brand", "Label", "Name",
                    "Default"));
        }
    }

    /** A product under a fixed id, so the test knows which page of the category it lands on (pages follow the id). */
    private static void save(String categoryId, String productId, String pimId, boolean enabled) {
        Product product = new Product(categoryId, pimId, "5900000000008", productId, "Brand", "Label", "Name", "Default");
        product.setProductId(productId);
        product.setEnabled(enabled);
        products.save(product);
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

    @Test
    void productsWithPimIdReadEveryPageEvenWhenAPageIsFilteredEmpty() {
        // given: the first page holds a disabled product and one without a PIM entry, the filter drops both
        save("cat-with-pim", "p1", "pim-1", false);
        save("cat-with-pim", "p2", null, true);
        save("cat-with-pim", "p3", "pim-3", true);
        save("cat-with-pim", "p4", "pim-4", true);
        save("cat-with-pim-other", "p5", "pim-5", true);

        // when: two items a page
        List<Product> found = products.findAllProductsWithPimId("cat-with-pim", true, 2);

        // then
        assertThat(found).extracting(Product::getProductId).containsExactlyInAnyOrder("p3", "p4");
    }

    @Test
    void findAllByPimIdReadsEveryPage() {
        // given
        save("cat-pim-a", "pa", "pim-shared", true);
        save("cat-pim-b", "pb", "pim-shared", false);
        save("cat-pim-c", "pc", "pim-shared", true);
        save("cat-pim-d", "pd", "pim-other", true);

        // when: one item a page
        List<Product> found = products.findAllByPimId("pim-shared", 1);

        // then
        assertThat(found).extracting(Product::getCategoryId).containsExactlyInAnyOrder("cat-pim-a", "cat-pim-b", "cat-pim-c");
    }
}
