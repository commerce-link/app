package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.templates.EmailTemplatesRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCopyServiceTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private EmailTemplatesRepository emailTemplatesRepository;

    @Captor
    private ArgumentCaptor<List<Product>> productsCaptor;

    @InjectMocks
    private StoreCopyService storeCopyService;

    @Test
    void copiedServiceDefinitionAndItsProductsKeepTheServiceFlag() {
        // given
        Store source = new Store();
        source.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(source);

        CategoryDefinition serviceDefinition = new CategoryDefinition();
        serviceDefinition.setCategoryId("cat-def-1");
        serviceDefinition.setName("Montaż");
        serviceDefinition.setService(true);
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(serviceDefinition));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        Product serviceProduct = new Product("cat-def-1");
        serviceProduct.setName("Montaż PC");
        serviceProduct.setService(true);
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of(serviceProduct));

        // when
        storeCopyService.copyStore("store-1", "Kopia");

        // then
        ArgumentCaptor<ProductCatalog> catalogCaptor = ArgumentCaptor.forClass(ProductCatalog.class);
        verify(productCatalogRepository).save(catalogCaptor.capture());
        assertThat(catalogCaptor.getValue().getCategories().get(0).isService()).isTrue();

        verify(productRepository).batchSave(productsCaptor.capture());
        assertThat(productsCaptor.getValue().get(0).isService()).isTrue();
    }
}
