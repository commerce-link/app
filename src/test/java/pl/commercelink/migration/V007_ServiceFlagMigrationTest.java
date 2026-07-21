package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V007_ServiceFlagMigrationTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private BasketsRepository basketsRepository;

    @InjectMocks
    private V007_ServiceFlagMigration migration;

    private ProductCatalog catalogWithLegacyServicesDefinition(String catalogId, String definitionCategoryId, String definitionName) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId(definitionCategoryId);
        definition.setName(definitionName);
        definition.setCategory("Services");

        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Catalog");
        catalog.setCatalogId(catalogId);
        catalog.setCategories(new ArrayList<>(List.of(definition)));
        return catalog;
    }

    @Test
    void secondRunIsANoOp() {
        // given
        ProductCatalog catalog = catalogWithLegacyServicesDefinition("catalog-1", "cat-def-1", "Serwis");
        when(storesRepository.findAll()).thenReturn(List.of(storeWith(STORE_ID)));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));

        Product product = new Product("cat-def-1");
        product.setService(false);
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of(product));

        Order order = new Order(STORE_ID);
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of(order));

        OrderItem orderItem = new OrderItem(order.getOrderId(), "Services", "Montaz", 1, 10, "sku-1", false);
        when(orderItemsRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(orderItem));

        BasketItem basketItem = new BasketItem("id-1", "Montaz", "mfn-1", "Services", 10, 0, 1, "catalog-1", 1, false);
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketItems(new ArrayList<>(List.of(basketItem)));
        when(basketsRepository.findAll(STORE_ID)).thenReturn(List.of(basket));

        // when
        migration.migrate();
        migration.migrate();

        // then
        verify(productCatalogRepository, times(1)).save(catalog);
        verify(productRepository, times(1)).save(product);
        verify(orderItemsRepository, times(1)).save(orderItem);
        verify(basketsRepository, times(1)).save(basket);

        assertThat(product.isService()).isTrue();
        assertThat(orderItem.isService()).isTrue();
        assertThat(orderItem.getCategory()).isNull();
        assertThat(basketItem.isService()).isTrue();
        assertThat(basketItem.getCategory()).isNull();
    }

    @Test
    void categoryDefinitionsWithLegacyServicesCategoryAreClearedAndCollected() {
        // given
        ProductCatalog catalog = catalogWithLegacyServicesDefinition("catalog-1", "cat-def-1", "Serwis");
        when(storesRepository.findAll()).thenReturn(List.of(storeWith(STORE_ID)));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of());
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of());
        when(basketsRepository.findAll(STORE_ID)).thenReturn(List.of());

        // when
        migration.migrate();

        // then
        assertThat(catalog.getCategories().get(0).getCategory()).isNull();
        verify(productCatalogRepository).save(catalog);
    }

    @Test
    void productsUnderFormerServiceDefinitionGetFlaggedWhenMissing() {
        // given
        ProductCatalog catalog = catalogWithLegacyServicesDefinition("catalog-1", "cat-def-1", "Serwis");
        when(storesRepository.findAll()).thenReturn(List.of(storeWith(STORE_ID)));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));

        Product alreadyFlagged = new Product("cat-def-1");
        alreadyFlagged.setService(true);
        Product missingFlag = new Product("cat-def-1");
        missingFlag.setService(false);
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of(alreadyFlagged, missingFlag));
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of());
        when(basketsRepository.findAll(STORE_ID)).thenReturn(List.of());

        // when
        migration.migrate();

        // then
        assertThat(alreadyFlagged.isService()).isTrue();
        assertThat(missingFlag.isService()).isTrue();
        verify(productRepository, times(1)).save(missingFlag);
        verify(productRepository, times(0)).save(alreadyFlagged);
    }

    @Test
    void orderItemMigrationClearsLegacyCategoryAndFlagsByDefinitionName() {
        // given
        ProductCatalog catalog = catalogWithLegacyServicesDefinition("catalog-1", "cat-def-1", "Konsultacje");
        when(storesRepository.findAll()).thenReturn(List.of(storeWith(STORE_ID)));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of());
        when(basketsRepository.findAll(STORE_ID)).thenReturn(List.of());

        Order order = new Order(STORE_ID);
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of(order));

        OrderItem alreadyFlaggedLegacyItem = new OrderItem(order.getOrderId(), "Services", "Montaz", 1, 10, "sku-1", false);
        alreadyFlaggedLegacyItem.setService(true);
        OrderItem nameMatchedItem = new OrderItem(order.getOrderId(), "Konsultacje", "Doradztwo", 1, 20, "sku-2", false);
        when(orderItemsRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(alreadyFlaggedLegacyItem, nameMatchedItem));

        // when
        migration.migrate();

        // then
        assertThat(alreadyFlaggedLegacyItem.isService()).isTrue();
        assertThat(alreadyFlaggedLegacyItem.getCategory()).isNull();

        assertThat(nameMatchedItem.isService()).isTrue();
        assertThat(nameMatchedItem.getCategory()).isEqualTo("Konsultacje");

        verify(orderItemsRepository).save(alreadyFlaggedLegacyItem);
        verify(orderItemsRepository).save(nameMatchedItem);
    }

    @Test
    void basketItemMigrationClearsLegacyCategoryAndFlagsByDefinitionName() {
        // given
        ProductCatalog catalog = catalogWithLegacyServicesDefinition("catalog-1", "cat-def-1", "Konsultacje");
        when(storesRepository.findAll()).thenReturn(List.of(storeWith(STORE_ID)));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of());
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of());

        BasketItem alreadyFlaggedLegacyItem = new BasketItem("id-1", "Montaz", "mfn-1", "Services", 10, 0, 1, "catalog-1", 1, false);
        alreadyFlaggedLegacyItem.setService(true);
        BasketItem nameMatchedItem = new BasketItem("id-2", "Doradztwo", "mfn-2", "Konsultacje", 20, 0, 1, "catalog-1", 1, false);

        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketItems(new ArrayList<>(List.of(alreadyFlaggedLegacyItem, nameMatchedItem)));
        when(basketsRepository.findAll(STORE_ID)).thenReturn(List.of(basket));

        // when
        migration.migrate();

        // then
        assertThat(alreadyFlaggedLegacyItem.isService()).isTrue();
        assertThat(alreadyFlaggedLegacyItem.getCategory()).isNull();

        assertThat(nameMatchedItem.isService()).isTrue();
        assertThat(nameMatchedItem.getCategory()).isEqualTo("Konsultacje");

        verify(basketsRepository).save(basket);
    }

    private static Store storeWith(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }
}
