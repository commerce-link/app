package pl.commercelink.taxonomy;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.offer.imports.CsvOfferImporter;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.OfferController;
import pl.commercelink.web.OrdersController;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GOLDEN characterization of the 5 {@code ProductCategory.valueOf(...)} call-sites and their
 * divergent unknown-input semantics (throw vs. Other). This test FREEZES current behavior; if an
 * assertion disagreed with reality, the assertion was corrected to match the code, never vice versa.
 *
 * Semantics frozen:
 *  - PricelistRepository#mapFieldsToObject  : "OS" alias -> Software; unknown -> IllegalArgumentException
 *  - CsvOfferImporter#mapToBasketItem       : unknown -> IllegalArgumentException
 *  - TaxonomyParser#parseCategory           : unknown -> Other (try/catch swallows) -- CONTRAST, no throw
 *  - OfferController / OrdersController      : inline ProductCategory.valueOf(@RequestParam) on a
 *                                             Spring-bound handler; unreachable via pure Mockito, so the
 *                                             bare valueOf semantics are frozen directly here and the
 *                                             controllers propagate it as an HTTP-layer throw.
 */
@ExtendWith(MockitoExtension.class)
class GoldenValueOfSitesTest {

    private static final String PRICELIST_HEADER =
            "PimId;Ean;ManufacturerCode;Brand;Label;Name;Category;Price;Qty";

    @Mock
    private FileStorage fileStorage;

    // ---- SITE 1: PricelistRepository (alias "OS" -> Software) ----

    @Test
    void pricelistRepositoryMapsOsAliasToSoftware() {
        // given
        PricelistRepository repository = new PricelistRepository(fileStorage, "bucket");
        String key = "catalogId/pricelistId.csv";
        String csv = PRICELIST_HEADER + "\n" +
                "pim1;ean1;mfc1;brand1;label1;name1;OS;100;5";
        when(fileStorage.canRead("bucket", key)).thenReturn(true);
        when(fileStorage.get("bucket", key)).thenReturn(new InputStreamReader(new ByteArrayInputStream(csv.getBytes())));

        // when
        Pricelist pricelist = repository.find("catalogId", "pricelistId");

        // then
        assertEquals(ProductCategory.Software, pricelist.getAvailabilityAndPrices().get(0).getCategory());
    }

    // ---- SITE 1b: PricelistRepository (unknown -> THROW) ----

    @Test
    void pricelistRepositoryThrowsOnUnknownCategory() {
        // given
        PricelistRepository repository = new PricelistRepository(fileStorage, "bucket");
        String key = "catalogId/pricelistId.csv";
        String csv = PRICELIST_HEADER + "\n" +
                "pim1;ean1;mfc1;brand1;label1;name1;NotARealCategory;100;5";
        when(fileStorage.canRead("bucket", key)).thenReturn(true);
        when(fileStorage.get("bucket", key)).thenReturn(new InputStreamReader(new ByteArrayInputStream(csv.getBytes())));

        // when / then
        assertThrows(IllegalArgumentException.class, () -> repository.find("catalogId", "pricelistId"));
    }

    // ---- SITE 2: CsvOfferImporter (unknown -> THROW) ----

    @Test
    void csvOfferImporterThrowsOnUnknownCategory() throws IOException {
        // given
        CsvOfferImporter importer = new CsvOfferImporter();
        // row layout (header skipped): [0]=category [1]=name [2]=qty [3]=price [4]=double [5]=mfc
        String csv = "category;name;qty;price;d;mfc\n" +
                "NotARealCategory;Some Name;3;1000;0.0;MFC-1";
        OfferCreationDto dto = csvDto(csv);

        // when / then
        assertThrows(IllegalArgumentException.class, () -> importer.importOffer(dto));
    }

    // ---- SITE 2b: CsvOfferImporter (known category maps through) ----

    @Test
    void csvOfferImporterMapsKnownCategory() throws IOException {
        // given
        CsvOfferImporter importer = new CsvOfferImporter();
        String csv = "category;name;qty;price;d;mfc\n" +
                "CPU;Some Name;3;1000;0.0;MFC-1";
        OfferCreationDto dto = csvDto(csv);

        // when
        List<BasketItem> items = importer.importOffer(dto);

        // then
        assertEquals(ProductCategory.CPU, items.get(0).getCategory());
    }

    // ---- SITE 3: TaxonomyParser (unknown -> Other) -- CONTRAST: does NOT throw ----

    @Test
    void taxonomyParserMapsUnknownCategoryToOther() {
        // given
        String[] row = {"123", "MFN", "Brand", "Name", "NotARealCategory", "5"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertEquals(ProductCategory.Other, parsed.category());
    }

    @Test
    void taxonomyParserMapsKnownCategoryThrough() {
        // given
        String[] row = {"123", "MFN", "Brand", "Name", "Laptops", "5"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertEquals(ProductCategory.Laptops, parsed.category());
    }

    /**
     * MUTATION GUARD: if TaxonomyParser#parseCategory loses its try/catch and becomes a bare
     * ProductCategory.valueOf(value), this test fails because fromCsvRow would propagate
     * IllegalArgumentException instead of returning Other. Keeps the swallow-vs-throw contrast honest.
     */
    @Test
    void taxonomyParserSwallowsValueOfFailureInsteadOfThrowing() {
        // given
        String[] row = {"123", "MFN", "Brand", "Name", "definitely-not-a-category", "5"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertEquals(ProductCategory.Other, parsed.category());
    }

    // ---- SITE 4 & 5: OfferController / OrdersController inline valueOf ----
    // Both controllers call ProductCategory.valueOf(category) on a @RequestParam String inside an MVC
    // handler. The handler bodies are plain public methods (the @RequestParam is already a String, so no
    // Spring type binding is needed to reach the valueOf line); getStoreId() returns null without a
    // security context. We therefore invoke the REAL handlers directly with mocked collaborators (fields
    // set reflectively, no @SpringBootTest) and assert that an unknown category propagates the REAL
    // IllegalArgumentException from valueOf, and that a known category resolves through to the
    // pricelist lookup.

    @Test
    void offerControllerPropagatesValueOfThrowOnUnknownCategory() throws Exception {
        // given
        OfferController controller = new OfferController();
        BasketsRepository basketsRepository = mock(BasketsRepository.class);
        PricelistRepository pricelistRepository = mock(PricelistRepository.class);
        when(basketsRepository.findById(any(), eq("offer1"))).thenReturn(Optional.of(new Basket()));
        when(pricelistRepository.find("cat1", "pl1")).thenReturn(new Pricelist("pl1", List.of(
                new AvailabilityAndPrice("pim", "ean", "mfn", "brand", "label", "name",
                        ProductCategory.CPU, 100L, 5L, 1, 90L))));
        setField(controller, "basketsRepository", basketsRepository);
        setField(controller, "pricelistRepository", pricelistRepository);

        // when / then (REAL handler reaches ProductCategory.valueOf(category) and propagates the throw)
        assertThrows(IllegalArgumentException.class, () -> controller.addOfferItemFromPriceList(
                "offer1", "cat1", "pl1", "NotARealCategory", "label", "name"));
    }

    @Test
    void ordersControllerPropagatesValueOfThrowOnUnknownCategory() throws Exception {
        // given
        OrdersController controller = new OrdersController();
        StoresRepository storesRepository = mock(StoresRepository.class);
        OrdersRepository ordersRepository = mock(OrdersRepository.class);
        PricelistRepository pricelistRepository = mock(PricelistRepository.class);
        when(pricelistRepository.find("cat1", "pl1")).thenReturn(new Pricelist("pl1", List.of(
                new AvailabilityAndPrice("pim", "ean", "mfn", "brand", "label", "name",
                        ProductCategory.CPU, 100L, 5L, 1, 90L))));
        setField(controller, "storesRepository", storesRepository);
        setField(controller, "ordersRepository", ordersRepository);
        setField(controller, "pricelistRepository", pricelistRepository);

        // when / then (REAL handler reaches ProductCategory.valueOf(category) and propagates the throw)
        assertThrows(IllegalArgumentException.class, () -> controller.addOrderItemFromPriceList(
                "order1", "cat1", "pl1", "NotARealCategory", "label", "name"));
    }

    @Test
    void ordersControllerResolvesKnownCategoryThroughToPricelistLookup() throws Exception {
        // given
        // known category resolves: valueOf succeeds, findByCategoryLabelAndName runs (no match -> empty),
        // and the REAL handler returns the redirect using the order id.
        OrdersController controller = new OrdersController();
        StoresRepository storesRepository = mock(StoresRepository.class);
        OrdersRepository ordersRepository = mock(OrdersRepository.class);
        PricelistRepository pricelistRepository = mock(PricelistRepository.class);
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn("order1");
        when(ordersRepository.findById(any(), eq("order1"))).thenReturn(order);
        when(pricelistRepository.find("cat1", "pl1")).thenReturn(new Pricelist("pl1", List.of(
                new AvailabilityAndPrice("pim", "ean", "mfn", "brand", "label", "name",
                        ProductCategory.GPU, 100L, 5L, 1, 90L))));
        setField(controller, "storesRepository", storesRepository);
        setField(controller, "ordersRepository", ordersRepository);
        setField(controller, "pricelistRepository", pricelistRepository);

        // when (REAL handler resolves "GPU" via valueOf and completes the lookup)
        String view = controller.addOrderItemFromPriceList(
                "order1", "cat1", "pl1", "GPU", "noMatchLabel", "noMatchName");

        // then
        assertEquals("redirect:/dashboard/orders/order1#orderItemsForm", view);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static OfferCreationDto csvDto(String csv) throws IOException {
        org.springframework.web.multipart.MultipartFile file =
                org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        OfferCreationDto dto = new OfferCreationDto();
        dto.setCsvFile(file);
        return dto;
    }
}
