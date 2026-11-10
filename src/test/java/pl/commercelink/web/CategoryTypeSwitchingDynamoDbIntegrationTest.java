package pl.commercelink.web;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.externaloffers.ExternalOfferLinkRepository;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.marketplace.MarketplaceExportRun;
import pl.commercelink.marketplace.MarketplaceExportRunService;
import pl.commercelink.marketplace.MarketplaceOfferExportEventListener;
import pl.commercelink.marketplace.MarketplaceOfferExportRequest;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.DynamicCategoryProducts;
import pl.commercelink.products.ManagedCategoryProducts;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductCleanupScheduler;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.testsupport.DynamoDbLocal;
import pl.commercelink.testsupport.DynamoDbLocalImage;
import pl.commercelink.testsupport.RetryingOptimisticLockingExecutor;
import pl.commercelink.testsupport.SecurityContextLogin;
import pl.commercelink.web.catalog.CatalogAccess;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * An operator switching one catalog category between Automatyczna and Ręczna several times, end to end against
 * DynamoDB Local: every switch is the POST of the Basics form through CatalogCategoryController, saved by the real
 * CategoryDefinitions, ManagedCategoryProducts, DynamicCategoryProducts and ProductRecommendationEngine, and the
 * retention runs through the real ProductCleanupScheduler. Only the inventory and the PIM are stand-ins, so a test can
 * change what the automatic list offers between two switches; days pass by moving the category's typeChangedAt back.
 * Every test has a store of its own, so a cleanup run sees nothing another test left behind.
 */
@Testcontainers(disabledWithoutDocker = true)
class CategoryTypeSwitchingDynamoDbIntegrationTest {

    private static final String PIM_CATEGORY = "pim-gpu";
    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static OptimisticLockingExecutor executor;

    private ProductRepository products;
    private ProductCatalogRepository catalogs;
    private Store store;
    private StoresRepository stores;
    private PimCatalog pim;
    private Inventory inventory;
    private ResourceBundleMessageSource messages;
    private CategoryDefinitions definitions;
    private DynamicCategoryProducts dynamicCategoryProducts;
    private ProductCleanupScheduler cleanup;
    private MockMvc mvc;

    /**
     * What the inventory and the PIM hold for the category's PIM category right now; a test changes it between switches.
     * Built when the offer changes, not inside the stand-ins' answers.
     */
    private List<MatchedInventory> groups = List.of();
    private List<PimEntry> entries = List.of();

    private String storeId;
    private String catalogId;
    private String categoryId;

    private record Offer(String pimId, String ean, String name) {
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @BeforeAll
    static void createTheTables() {
        client = DynamoDbLocal.clientOf(DYNAMODB);
        DynamoDbLocal.createTables(client, Product.class, ProductCatalog.class);
        executor = RetryingOptimisticLockingExecutor.create();
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        storeId = "store-" + UUID.randomUUID();
        catalogId = "catalog-" + UUID.randomUUID();
        categoryId = "category-" + UUID.randomUUID();
        products = new ProductRepository(client);
        catalogs = new ProductCatalogRepository(client);

        store = mock(Store.class);
        when(store.getStoreId()).thenReturn(storeId);
        stores = mock(StoresRepository.class);
        when(stores.findAll()).thenReturn(List.of(store));
        when(stores.findById(storeId)).thenReturn(store);

        InventoryView view = mock(InventoryView.class);
        when(view.findAllByProductCategoryIds(any())).thenAnswer(call -> Map.of(PIM_CATEGORY, groups));
        inventory = mock(Inventory.class);
        when(inventory.withEnabledSuppliersAndWarehouseData(storeId, SupplierScope.PRICING)).thenReturn(view);
        when(inventory.withEnabledSuppliersOnly(storeId)).thenReturn(view);
        pim = mock(PimCatalog.class);
        when(pim.withStoreData(any())).thenAnswer(call -> new PimEntryIndex(entries, UnaryOperator.identity(),
                UnaryOperator.identity()).forStore(call.getArgument(0)));
        when(pim.withGlobalData()).thenAnswer(call -> new PimEntryIndex(entries, UnaryOperator.identity(),
                UnaryOperator.identity()).global());

        CatalogPlacement placement = mock(CatalogPlacement.class);
        dynamicCategoryProducts = new DynamicCategoryProducts(engineOver(products), pim, inventory);
        definitions = new CategoryDefinitions(catalogs, products, executor, placement,
                new ManagedCategoryProducts(products, placement, new StorePimAliases(pim)));
        messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        mvc = mvcReadingTheCatalogThrough(catalogs);
        cleanup = cleanupOver(products);
        SecurityContextLogin.logInAs("ADMIN", storeId);
    }

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    // --- scenarios ------------------------------------------------------------------------------------------------

    @Test
    void manualToAutomaticAndBackWithinTheRetentionSkippingTheListRestoresTheProductsAsTheyWere() throws Exception {
        // given: a manual category with a product renamed by the operator and one switched off
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));
        stored("pim-2", "5901000000028", product -> product.setEnabled(false));
        List<Product> before = inCategory();

        // when: six days automatic, one day short of the retention, when the cleanup runs
        save("Dynamic", null);
        daysPass(6);
        cleanup.cleanUpOrphanedProducts("tick");
        MvcResult back = save("Managed", "skip");

        // then
        assertThat(typeOfCategory()).isEqualTo(CategoryDefinitionType.Managed);
        assertThat(inCategory()).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrderElementsOf(before);
        assertThat(savedMessage(back)).contains("Produktów z listy automatycznej nie zapisano");
    }

    @Test
    void manualToAutomaticAndBackWithinTheRetentionStoringTheListAddsOnlyWhatWasMissing() throws Exception {
        // given: one product of the manual category, edited, switched off and approved for Allegro
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"),
                offer("pim-3", "5901000000035", "MSI RTX 5080"));
        stored("pim-1", "5901000000099", product -> {
            product.setName("Moja karta");
            product.setEnabled(false);
            product.getMarketplaces().add("allegro");
        });

        // when
        save("Dynamic", null);
        MvcResult back = save("Managed", "store");

        // then
        assertThat(inCategory()).extracting(Product::getPimId).containsExactlyInAnyOrder("pim-1", "pim-2", "pim-3");
        Product edited = productOf("pim-1");
        assertThat(edited.getName()).isEqualTo("Moja karta");
        assertThat(edited.isEnabled()).isFalse();
        assertThat(edited.getMarketplaces()).containsExactly("allegro");
        assertThat(edited.getEan()).isEqualTo("5901000000099");
        assertThat(savedMessage(back)).contains("Produkty utworzone z listy automatycznej: 2.")
                .contains("Produkty, które już były w kategorii i zostały bez zmian: 1.");
    }

    @Test
    void automaticToManualTwiceWithAChangedListAddsOnlyTheNewProducts() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));

        // when: the supplier drops one card and adds another while the category is automatic again
        MvcResult first = save("Managed", "store");
        save("Dynamic", null);
        offers(offer("pim-2", "5901000000028", "MSI RTX 5070"), offer("pim-3", "5901000000035", "MSI RTX 5080"));
        MvcResult second = save("Managed", "store");

        // then
        assertThat(inCategory()).extracting(Product::getPimId).containsExactlyInAnyOrder("pim-1", "pim-2", "pim-3");
        assertThat(savedMessage(first)).contains("Produkty utworzone z listy automatycznej: 2.");
        assertThat(savedMessage(second)).contains("Produkty utworzone z listy automatycznej: 1.")
                .contains("Produkty, które już były w kategorii i zostały bez zmian: 2.");
    }

    @Test
    void aLongSequenceOfSwitchesNeverStoresAProductTwice() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));

        // when
        save("Managed", "store");
        edit("pim-1", product -> product.setName("Moja karta"));
        save("Dynamic", null);
        save("Managed", "skip");
        save("Dynamic", null);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"),
                offer("pim-3", "5901000000035", "MSI RTX 5080"));
        save("Managed", "store");
        save("Dynamic", null);
        daysPass(5);
        cleanup.cleanUpOrphanedProducts("tick");
        save("Managed", "store");

        // then
        assertThat(inCategory()).extracting(Product::getPimId).containsExactlyInAnyOrder("pim-1", "pim-2", "pim-3");
        assertThat(inCategory()).extracting(Product::getEan).doesNotHaveDuplicates();
        assertThat(productOf("pim-1").getName()).isEqualTo("Moja karta");
    }

    @Test
    void manualToAutomaticPastTheRetentionLosesTheProductsAndManualAgainStartsFromThePim() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));

        // when
        save("Dynamic", null);
        daysPass(8);
        cleanup.cleanUpOrphanedProducts("tick");
        List<Product> afterTheCleanup = inCategory();
        MvcResult back = save("Managed", "store");

        // then
        assertThat(afterTheCleanup).isEmpty();
        assertThat(inCategory()).singleElement().satisfies(product -> {
            assertThat(product.getPimId()).isEqualTo("pim-1");
            assertThat(product.getName()).isEqualTo("MSI RTX 5060");
        });
        assertThat(savedMessage(back)).contains("Produkty utworzone z listy automatycznej: 1.")
                .doesNotContain("zostały bez zmian");
    }

    @Test
    void switchingBackToManualBeforeTheCleanupKeepsTheProducts() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));

        // when: the retention has passed, but the category is manual again when the cleanup runs
        save("Dynamic", null);
        daysPass(8);
        save("Managed", "skip");
        cleanup.cleanUpOrphanedProducts("tick");

        // then
        assertThat(inCategory()).singleElement().extracting(Product::getName).isEqualTo("Moja karta");
    }

    @Test
    void everySwitchToAutomaticStartsTheRetentionAgain() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));

        // when: six days automatic, manual for a moment, automatic again; nine days in all, three since the last switch
        save("Dynamic", null);
        daysPass(6);
        save("Managed", "skip");
        save("Dynamic", null);
        daysPass(3);
        cleanup.cleanUpOrphanedProducts("tick");
        List<Product> threeDaysAfterTheLastSwitch = inCategory();
        daysPass(5);
        cleanup.cleanUpOrphanedProducts("tick");

        // then
        assertThat(threeDaysAfterTheLastSwitch).hasSize(1);
        assertThat(inCategory()).isEmpty();
    }

    @Test
    void aSwitchToManualWhileTheCleanupRunsKeepsTheNewProducts() throws Exception {
        // given: an automatic category past the retention, with a product left from when it was manual
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));
        save("Dynamic", null);
        daysPass(8);
        ProductCleanupScheduler racingCleanup = cleanupOver(productsReadWhile(() -> save("Managed", "store")));

        // when
        racingCleanup.cleanUpOrphanedProducts("tick");

        // then
        assertThat(typeOfCategory()).isEqualTo(CategoryDefinitionType.Managed);
        assertThat(inCategory()).extracting(Product::getPimId).containsExactlyInAnyOrder("pim-1", "pim-2");
        assertThat(productOf("pim-1").getName()).isEqualTo("Moja karta");
    }

    @Test
    void anAutomaticCategoryLeavesTheMarketplaceAndComesBackWhenManualAgain() throws Exception {
        // given: a manual category exported to Allegro, one of the offered cards stored in it
        categoryOf(CategoryDefinitionType.Managed);
        exportedTo("Allegro");
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));
        MarketplaceExport allegro = marketplaceExport("Allegro");

        // when: one export run per state, the automatic one with both cards priced on the price list
        ExportRun whileManual = allegro.run();
        save("Dynamic", null);
        ExportRun whileAutomatic = allegro.run();
        save("Managed", "skip");
        ExportRun manualAgain = allegro.run();

        // then: the products kept for a return to manual are not the automatic category's offers
        assertThat(whileManual.published()).containsExactly("pim-1");
        assertThat(whileAutomatic.published()).isEmpty();
        assertThat(whileAutomatic.removed()).containsExactly("pim-1");
        assertThat(manualAgain.published()).containsExactly("pim-1");
    }

    @Test
    void aProductSavedWhileTheCleanupRunsIsDeletedAllTheSame() throws Exception {
        // given: an automatic category past the retention, one of its products moved onto another entry mid-run
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));
        stored("pim-2", "5901000000028", product -> product.setName("Druga karta"));
        save("Dynamic", null);
        daysPass(8);
        ProductCleanupScheduler cleanupMeetingASave = cleanupOver(productsSavedJustAfterTheRead(
                () -> edit("pim-2", product -> product.setPimId("pim-2-moved"))));

        // when
        cleanupMeetingASave.cleanUpOrphanedProducts("tick");

        // then
        assertThat(inCategory()).isEmpty();
    }

    @Test
    void theAutomaticListKeepsOfferingTheProductsLeftInTheTableUnderTheirPimData() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Managed);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));
        stored("pim-1", "5901000000011", product -> product.setName("Moja karta"));

        // when
        save("Dynamic", null);
        List<ManagedCategoryProducts.Candidate> automaticList = dynamicCategoryProducts.candidates(storeId, category());

        // then
        assertThat(automaticList).extracting(candidate -> candidate.product().getPimId())
                .containsExactlyInAnyOrder("pim-1", "pim-2");
        assertThat(automaticList).extracting(candidate -> candidate.product().getName())
                .containsExactlyInAnyOrder("MSI RTX 5060", "MSI RTX 5070");
        assertThat(productOf("pim-1").getName()).isEqualTo("Moja karta");
    }

    @Test
    void aProductDeletedWhileManualComesBackWhenTheListIsStoredAgain() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));

        // when
        save("Managed", "store");
        products.deleteWhateverItsVersion(productOf("pim-2"));
        save("Dynamic", null);
        save("Managed", "store");

        // then
        assertThat(inCategory()).extracting(Product::getPimId).containsExactlyInAnyOrder("pim-1", "pim-2");
    }

    @Test
    void anEntryInTwoPriceGroupsBecomesOneProductAcrossSwitches() throws Exception {
        // given: one global entry whose two codes the inventory keeps in two groups at two prices
        categoryOf(CategoryDefinitionType.Dynamic);
        inventoryHolds(List.of(globalEntry("pim-1", "MSI RTX 5060", "5901000000011", "5901000000042")),
                List.of(group("5901000000011", "MSI RTX 5060", 1000.0), group("5901000000042", "MSI RTX 5060", 1200.0)));

        // when
        MvcResult first = save("Managed", "store");
        save("Dynamic", null);
        MvcResult second = save("Managed", "store");

        // then
        assertThat(inCategory()).singleElement().extracting(Product::getPimId).isEqualTo("pim-1");
        assertThat(savedMessage(first)).contains("Produkty utworzone z listy automatycznej: 1.");
        assertThat(savedMessage(second)).contains("Produkty utworzone z listy automatycznej: 0.");
    }

    @Test
    void aProductOnTheSourceOfTheStoresCloneIsNotStoredAgain() throws Exception {
        // given: the category holds a product on a global entry, under another EAN than the inventory knows; the store
        // has cloned that entry since, so the automatic list carries the clone
        categoryOf(CategoryDefinitionType.Managed);
        inventoryHolds(List.of(globalEntry("src-1", "MSI RTX 5060", "5901000000011"),
                        storeClone("clone-1", "src-1", "MSI RTX 5060 (sklep)", "5901000000011")),
                List.of(group("5901000000011", "MSI RTX 5060", 1000.0)));
        stored("src-1", "5901000000099", product -> product.setName("Moja karta"));

        // when
        save("Dynamic", null);
        List<ManagedCategoryProducts.Candidate> automaticList = dynamicCategoryProducts.candidates(storeId, category());
        save("Managed", "store");

        // then
        assertThat(automaticList).singleElement().extracting(candidate -> candidate.product().getPimId()).isEqualTo("clone-1");
        assertThat(inCategory()).singleElement().satisfies(product -> {
            assertThat(product.getPimId()).isEqualTo("src-1");
            assertThat(product.getName()).isEqualTo("Moja karta");
        });
    }

    @Test
    void theSameFormSentAgainAfterTheSwitchStoresNothingMore() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));

        // when: sent again once the first save has finished, as a second click or a tab opened earlier sends it
        MvcResult first = save("Managed", "store");
        MvcResult again = save("Managed", "store");

        // then
        assertThat(inCategory()).hasSize(2);
        assertThat(savedMessage(first)).contains("Produkty utworzone z listy automatycznej: 2.");
        assertThat(savedMessage(again)).doesNotContain("listy automatycznej");
    }

    @Test
    void twoRequestsThatBothReadTheCategoryAutomaticStoreItsListOnce() throws Exception {
        // given: two tabs saving at the same moment, each having read the category before either one saves
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"), offer("pim-2", "5901000000028", "MSI RTX 5070"));
        CyclicBarrier bothRead = new CyclicBarrier(2);
        MockMvc racing = mvcReadingTheCatalogThrough(new ProductCatalogRepository(client) {
            @Override
            public ProductCatalog findById(String store, String catalog) {
                ProductCatalog read = super.findById(store, catalog);
                try {
                    bothRead.await(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("The other request never read the category", e);
                }
                return read;
            }
        });
        ExecutorService tabs = Executors.newFixedThreadPool(2);

        // when
        List<String> savedMessages;
        try {
            var first = tabs.submit(asOperator(() -> racing.perform(form("Managed", "store")).andReturn()));
            var second = tabs.submit(asOperator(() -> racing.perform(form("Managed", "store")).andReturn()));
            savedMessages = List.of(savedMessage(first.get(60, TimeUnit.SECONDS)), savedMessage(second.get(60, TimeUnit.SECONDS)));
        } finally {
            tabs.shutdownNow();
        }

        // then
        assertThat(typeOfCategory()).isEqualTo(CategoryDefinitionType.Managed);
        assertThat(inCategory()).extracting(Product::getPimId).containsExactlyInAnyOrder("pim-1", "pim-2");
        // the request that lost the race finds the category manual and reports no conversion at all
        assertThat(savedMessages).filteredOn(message -> message.contains("listy automatycznej"))
                .singleElement().asString().contains("Produkty utworzone z listy automatycznej: 2.");
    }

    @Test
    void turningManualWithoutAChoiceChangesNothingUntilOneIsMade() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"));

        // when
        MvcResult refused = save("Managed", null);
        CategoryDefinitionType afterTheRefusal = typeOfCategory();
        List<Product> productsAfterTheRefusal = inCategory();
        save("Managed", "store");

        // then
        assertThat(refused.getModelAndView().getModel().get("errors"))
                .isEqualTo(Map.of("dynamicProducts", "catalog.category.type.managed.choice.required"));
        assertThat(afterTheRefusal).isEqualTo(CategoryDefinitionType.Dynamic);
        assertThat(productsAfterTheRefusal).isEmpty();
        assertThat(typeOfCategory()).isEqualTo(CategoryDefinitionType.Managed);
        assertThat(inCategory()).hasSize(1);
    }

    @Test
    void aSwitchSentByTheScriptAnswersWithTheFormAndKeepsItsMessageForTheNextPage() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);
        offers(offer("pim-1", "5901000000011", "MSI RTX 5060"));

        // when
        MvcResult refused = mvc.perform(form("Managed", null).header("X-Requested-With", "fetch")).andReturn();
        MvcResult saved = mvc.perform(form("Managed", "store").header("X-Requested-With", "fetch")).andReturn();

        // then
        assertThat(refused.getResponse().getStatus()).isEqualTo(422);
        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(saved.getModelAndView().getModel().get("redirectTo"))
                .isEqualTo("/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/settings");
        assertThat(savedMessage(saved)).contains("Produkty utworzone z listy automatycznej: 1.");
        assertThat(inCategory()).hasSize(1);
    }

    @Test
    void anEmptyAutomaticListTurnsManualWithoutAskingAndWarns() throws Exception {
        // given
        categoryOf(CategoryDefinitionType.Dynamic);

        // when
        MvcResult saved = save("Managed", null);

        // then
        assertThat(typeOfCategory()).isEqualTo(CategoryDefinitionType.Managed);
        assertThat(inCategory()).isEmpty();
        assertThat(saved.getFlashMap().get("catalogWarning")).asString().contains("Lista automatyczna była pusta");
    }

    // --- the operator's actions and what the test reads back -----------------------------------------------------------

    /** The Basics form of the category; {@code dynamicProducts} null leaves the choice out. */
    private MockHttpServletRequestBuilder form(String type, String dynamicProducts) {
        MockHttpServletRequestBuilder form = post("/dashboard/catalogs/" + catalogId + "/category/" + categoryId + "/settings/basics")
                .locale(POLISH)
                .param("name", "Karty graficzne").param("type", type).param("maxQty", "1")
                .param("pimCategoryIds", PIM_CATEGORY);
        if (dynamicProducts != null) {
            form.param("dynamicProducts", dynamicProducts);
        }
        return form;
    }

    /** The form sent without JavaScript. */
    private MvcResult save(String type, String dynamicProducts) throws Exception {
        return mvc.perform(form(type, dynamicProducts)).andReturn();
    }

    /** A request of another thread, signed in as the operator there: the security context belongs to its thread. */
    private <T> Callable<T> asOperator(Callable<T> request) {
        return () -> {
            SecurityContextLogin.logInAs("ADMIN", storeId);
            try {
                return request.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private static String savedMessage(MvcResult result) {
        return String.valueOf(result.getFlashMap().get("settingsSavedMessage"));
    }

    private void categoryOf(CategoryDefinitionType type) {
        ProductCatalog catalog = new ProductCatalog(storeId, "Podzespoły");
        catalog.setCatalogId(catalogId);
        CategoryDefinition category = new CategoryDefinition().withName("Karty graficzne").withSequenceNumber(1)
                .withPriceDefinition(new PriceDefinition(1.0, 0, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP));
        category.setCategoryId(categoryId);
        category.setType(type);
        category.setPimCategoryIds(new ArrayList<>(List.of(PIM_CATEGORY)));
        catalog.getCategories().add(category);
        catalogs.save(catalog);
    }

    private CategoryDefinition category() {
        return catalogs.findById(storeId, catalogId).findCategoryDefinition(categoryId);
    }

    private CategoryDefinitionType typeOfCategory() {
        return category().getType();
    }

    /**
     * Days pass for the category: its last change of type moves that many days back from where the last save put it,
     * so a switch that failed to restart the retention would show as more days, never be covered by this helper.
     */
    private void daysPass(int days) {
        ProductCatalog catalog = catalogs.findById(storeId, catalogId);
        CategoryDefinition category = catalog.findCategoryDefinition(categoryId);
        category.setTypeChangedAt(category.getTypeChangedAt().minusDays(days));
        catalogs.save(catalog);
    }

    /** A product the operator added to the manual category by hand. */
    private void stored(String pimId, String ean, Consumer<Product> edits) {
        Product product = new Product(categoryId, pimId, ean, null, "MSI", "Karta", "Karta " + pimId,
                PriceDefinition.DEFAULT_PRICING_GROUP);
        edits.accept(product);
        products.save(product);
    }

    private void edit(String pimId, Consumer<Product> edits) {
        Product product = productOf(pimId);
        edits.accept(product);
        products.save(product);
    }

    private List<Product> inCategory() {
        return products.findAll(categoryId);
    }

    private Product productOf(String pimId) {
        return inCategory().stream().filter(product -> pimId.equals(product.getPimId())).findFirst().orElseThrow();
    }

    // --- the application wired over DynamoDB Local ----------------------------------------------------------------------

    /** The Basics page, reading the catalog at the start of each request through {@code catalogsForTheRequest}. */
    private MockMvc mvcReadingTheCatalogThrough(ProductCatalogRepository catalogsForTheRequest) {
        return MockMvcBuilders.standaloneSetup(new CatalogCategoryController(new CatalogAccess(catalogsForTheRequest, products),
                definitions, products, stores, mock(PimCategoryOptions.class), mock(MarketplaceConnections.class), inventory,
                messages, dynamicCategoryProducts)).build();
    }

    private ProductCleanupScheduler cleanupOver(ProductRepository productsAsTheCleanupReadsThem) {
        ProductCleanupScheduler scheduler = new ProductCleanupScheduler(catalogs, productsAsTheCleanupReadsThem, stores);
        ReflectionTestUtils.setField(scheduler, "cleanupDays", 7);
        return scheduler;
    }

    /**
     * The products table as a cleanup run reads it, with the operator's save landing in the middle of the run: after the
     * run has read the catalogs, just before it reads the category's products.
     */
    private ProductRepository productsReadWhile(ThrowingRunnable operatorSave) {
        return new ProductRepository(client) {
            private boolean saved;

            @Override
            public List<Product> findAll(String category) {
                if (!saved && category.equals(categoryId)) {
                    saved = true;
                    try {
                        operatorSave.run();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return super.findAll(category);
            }
        };
    }

    /** The products table as a cleanup run reads it, with a save landing right after the run read the category's products. */
    private ProductRepository productsSavedJustAfterTheRead(ThrowingRunnable save) {
        return new ProductRepository(client) {
            private boolean saved;

            @Override
            public List<Product> findAll(String category) {
                List<Product> read = super.findAll(category);
                if (!saved && category.equals(categoryId)) {
                    saved = true;
                    try {
                        save.run();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return read;
            }
        };
    }

    /** The real engine; Spring builds it through a package-private constructor, which a test outside its package opens. */
    private static ProductRecommendationEngine engineOver(ProductRepository products) throws ReflectiveOperationException {
        Constructor<ProductRecommendationEngine> constructor =
                ProductRecommendationEngine.class.getDeclaredConstructor(ProductRepository.class);
        constructor.setAccessible(true);
        return constructor.newInstance(products);
    }

    // --- the marketplace export over the same tables ------------------------------------------------------------------

    private record ExportRun(List<String> published, List<String> removed) {
    }

    /** One marketplace's offer export, run as the export queue runs it, each run starting from the one before. */
    private final class MarketplaceExport {

        private final MarketplaceOfferExportEventListener listener;
        private final MarketplaceProvider provider;
        private final String marketplace;

        private MarketplaceExport(MarketplaceOfferExportEventListener listener, MarketplaceProvider provider, String marketplace) {
            this.listener = listener;
            this.provider = provider;
            this.marketplace = marketplace;
        }

        ExportRun run() {
            List<String> published = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            doAnswer(call -> {
                published.addAll(offerIdsOf(call.getArgument(0)));
                removed.addAll(offerIdsOf(call.getArgument(1)));
                return null;
            }).when(provider).exportOffers(any(), any(), any());
            listener.handleMessage(new MarketplaceOfferExportRequest(marketplace, storeId, catalogId, "pricelist"));
            return new ExportRun(published, removed);
        }

        private static List<String> offerIdsOf(List<MarketplaceOffer> offers) {
            return offers.stream().map(MarketplaceOffer::productId).toList();
        }
    }

    private void exportedTo(String marketplace) {
        ProductCatalog catalog = catalogs.findById(storeId, catalogId);
        MarketplaceDefinition definition = new MarketplaceDefinition(marketplace, 1.0, 0, 0, 0, 0, 1);
        definition.setEnabled(true);
        catalog.findCategoryDefinition(categoryId).getMarketplaceDefinitions().add(definition);
        catalogs.save(catalog);
    }

    /**
     * The real export over the test's tables and PIM. Stand-ins: the price list (every offered card priced, as both an
     * automatic and a manual category price it), the warehouse stock, the marketplace and the stored history of runs.
     */
    private MarketplaceExport marketplaceExport(String marketplace) {
        when(store.hasActiveMarketplaceIntegration(marketplace)).thenReturn(true);
        MatchedInventory stock = mock(MatchedInventory.class);
        when(stock.atPricePoint(anyLong())).thenReturn(stock);
        when(stock.getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn(5L);
        InventoryView exportView = mock(InventoryView.class);
        when(exportView.findByProduct(any())).thenReturn(stock);
        when(inventory.withEnabledSuppliersAndWarehouseData(storeId)).thenReturn(exportView);
        PricelistRepository pricelists = mock(PricelistRepository.class);
        when(pricelists.find(storeId, catalogId, "pricelist")).thenAnswer(call -> new Pricelist("pricelist", entries.stream()
                .map(entry -> new AvailabilityAndPrice(entry.pimId(), entry.primaryGtin().orElse(null), null, "MSI", "Karta",
                        entry.name(), "Karty graficzne", 1000L, 5L, 2, 1000L, false))
                .toList()));
        MarketplaceProvider provider = mock(MarketplaceProvider.class);
        MarketplaceProviderFactory providers = mock(MarketplaceProviderFactory.class);
        when(providers.get(store, marketplace)).thenReturn(provider);
        AtomicReference<List<MarketplaceOfferSnapshot>> previousRun = new AtomicReference<>(List.of());
        MarketplaceExportRunService runs = mock(MarketplaceExportRunService.class);
        when(runs.loadPreviousExport(storeId, catalogId, marketplace)).thenAnswer(call -> previousRun.get());
        doAnswer(call -> {
            previousRun.set(call.getArgument(0, MarketplaceExportRun.class).toRows());
            return null;
        }).when(runs).saveRun(any());
        StoreActivity activity = mock(StoreActivity.class);
        when(activity.isActive(store)).thenReturn(true);
        ExternalOfferLinkRepository links = mock(ExternalOfferLinkRepository.class);
        when(links.findAll(storeId, marketplace)).thenReturn(List.of());
        MarketplaceOfferExportEventListener listener = new MarketplaceOfferExportEventListener(stores, catalogs, products,
                pricelists, inventory, providers, runs, activity, new StorePimAliases(pim), links);
        ReflectionTestUtils.setField(listener, "removalRetryCount", 3);
        ReflectionTestUtils.setField(listener, "unknownEntryMaxWaitingRuns", 3);
        return new MarketplaceExport(listener, provider, marketplace);
    }

    // --- the inventory and the PIM the automatic list is computed from -------------------------------------------------

    private static Offer offer(String pimId, String ean, String name) {
        return new Offer(pimId, ean, name);
    }

    /** The cards the inventory offers from now on, one global entry and one group each, all priced alike. */
    private void offers(Offer... now) {
        inventoryHolds(Arrays.stream(now).map(offer -> globalEntry(offer.pimId(), offer.name(), offer.ean())).toList(),
                Arrays.stream(now).map(offer -> group(offer.ean(), offer.name(), 1000.0)).toList());
    }

    private void inventoryHolds(List<PimEntry> pimEntries, List<MatchedInventory> inventoryGroups) {
        entries = pimEntries;
        groups = inventoryGroups;
    }

    private static PimEntry globalEntry(String pimId, String name, String... gtins) {
        return new PimEntry(pimId, gtinsOf(gtins), "MSI", name, "GPU", null, true, null, null);
    }

    /** The store's own copy of a global entry, as "Klonuj" in the PIM makes it. */
    private PimEntry storeClone(String pimId, String sourcePimId, String name, String... gtins) {
        return new PimEntry(pimId, gtinsOf(gtins), "MSI", name, "GPU", null, true, null, null, PIM_CATEGORY, storeId,
                sourcePimId);
    }

    private static List<PimIdentifier> gtinsOf(String... gtins) {
        return Arrays.stream(gtins).map(gtin -> new PimIdentifier(gtin, PimIdentifierType.GTIN)).toList();
    }

    private static MatchedInventory group(String ean, String name, double netPrice) {
        MatchedInventory group = mock(MatchedInventory.class);
        when(group.hasAnyOffers()).thenReturn(true);
        when(group.getInventoryKey()).thenReturn(InventoryKey.fromEan(ean));
        when(group.getLowestPrice()).thenReturn(Price.fromNet(netPrice));
        when(group.getTaxonomy()).thenReturn(new Taxonomy(ean, null, "MSI", name, "GPU", 1, null, null, null, PIM_CATEGORY));
        return group;
    }
}
