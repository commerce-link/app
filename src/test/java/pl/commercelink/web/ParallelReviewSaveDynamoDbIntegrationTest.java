package pl.commercelink.web;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RF-6 against DynamoDB Local: one review of the bulk add saved by two requests at the same moment. The guard reads
 * the category and then writes, so two requests that both read before either writes both find nothing; a barrier
 * after the read holds each request until the other has read too, which is exactly that interleaving, every time.
 * The category must end up with one product.
 */
@Testcontainers(disabledWithoutDocker = true)
class ParallelReviewSaveDynamoDbIntegrationTest {

    private static final String STORE_ID = "store-1";

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;

    private final ExecutorService requests = Executors.newFixedThreadPool(2);

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
    }

    @AfterEach
    void stopTheRequests() {
        requests.shutdownNow();
    }

    /** The products table, with every request held after its read of the category until the other one has read too. */
    static class BothReadBeforeEitherWrites extends ProductRepository {

        private final CyclicBarrier bothRead = new CyclicBarrier(2);

        BothReadBeforeEitherWrites(AmazonDynamoDB client) {
            super(client);
        }

        @Override
        public List<Product> findAll(String categoryId) {
            List<Product> read = super.findAll(categoryId);
            try {
                bothRead.await(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("The other request never read the category", e);
            }
            return read;
        }
    }

    @Test
    void oneReviewSavedByTwoParallelRequestsAddsTheProductOnce() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        catalog.setCatalogId("c1");
        CategoryDefinition category = new CategoryDefinition().withName("CPU")
                .withPriceDefinition(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        category.setCategoryId("cat-parallel");
        catalog.getCategories().add(category);
        CatalogProductsController controller = controllerOver(new BothReadBeforeEitherWrites(client), catalog, category);
        String reviewId = ProductsBulkAddForm.of(List.of()).getReviewId();

        // when -- both requests carry the same review, as a double click or a second tab sends it
        Future<String> first = requests.submit(() -> save(controller, review(reviewId)));
        Future<String> second = requests.submit(() -> save(controller, review(reviewId)));
        first.get(60, TimeUnit.SECONDS);
        second.get(60, TimeUnit.SECONDS);

        // then
        List<Product> stored = new ProductRepository(client).findAll("cat-parallel");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getEan()).isEqualTo("5900000000008");
    }

    /** The form as the binder builds it for one request: its own objects, the posted values. */
    private static ProductsBulkAddForm review(String reviewId) {
        ProductsBulkAddForm form = new ProductsBulkAddForm();
        form.setReviewId(reviewId);
        ProductsBulkAddForm.Row row = new ProductsBulkAddForm.Row();
        row.setName("AMD Ryzen 7 7800X3D");
        row.setEan("5900000000008");
        row.setManufacturerCode("MFN-FINAL-01");
        row.setBrand("AMD");
        row.setPricingGroup("Default");
        row.setAvailabilityType("BasedOnSupply");
        form.getProducts().add(row);
        return form;
    }

    private static String save(CatalogProductsController controller, ProductsBulkAddForm form) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            return controller.saveProducts("c1", "cat-parallel", form, new ExtendedModelMap(), Locale.ENGLISH,
                    new RedirectAttributesModelMap(), new MockHttpServletResponse());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static CatalogProductsController controllerOver(ProductRepository products, ProductCatalog catalog,
                                                            CategoryDefinition category) {
        CatalogAccess access = mock(CatalogAccess.class);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, "cat-parallel")).thenReturn(category);
        MatchedInventory nothing = mock(MatchedInventory.class);
        when(nothing.isEmpty()).thenReturn(true);
        InventoryView view = mock(InventoryView.class);
        when(view.findByInventoryKey(any())).thenReturn(nothing);
        Inventory inventory = mock(Inventory.class);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(view);
        return new CatalogProductsController(access, products, mock(StoresRepository.class),
                mock(ProductRecommendationEngine.class), inventory, mock(MarketplaceConnections.class),
                mock(PimCategoryOptions.class), mock(SupplierLabels.class), mock(PimCatalog.class), mock(BrandMapper.class),
                mock(MessageSource.class));
    }
}
