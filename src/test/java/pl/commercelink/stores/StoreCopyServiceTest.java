package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimLookup;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.templates.EmailTemplatesRepository;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

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
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimLookup sourceLookup;

    @Captor
    private ArgumentCaptor<List<Product>> productsCaptor;

    @InjectMocks
    private StoreCopyService storeCopyService;

    @Test
    void copiedDefinitionsKeepThePimCategoryIds() {
        // given
        Store source = new Store();
        source.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(source);

        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-def-1");
        definition.setName("Karty graficzne");
        definition.setPimCategoryIds(List.of("194", "195"));
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(definition));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        // when
        storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        ArgumentCaptor<ProductCatalog> catalogCaptor = ArgumentCaptor.forClass(ProductCatalog.class);
        verify(productCatalogRepository).save(catalogCaptor.capture());
        assertThat(catalogCaptor.getValue().getCategories().get(0).getPimCategoryIds()).containsExactly("194", "195");
    }

    @Test
    void copiedDefinitionsProductsKeepTheServiceFlag() {
        // given
        Store source = new Store();
        source.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(source);

        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-def-1");
        definition.setName("Montaż");
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(definition));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        Product serviceProduct = new Product("cat-def-1");
        serviceProduct.setName("Montaż PC");
        serviceProduct.setService(true);
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of(serviceProduct));

        // when
        storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        verify(productRepository).batchSave(productsCaptor.capture());
        assertThat(productsCaptor.getValue().get(0).isService()).isTrue();
    }

    @Test
    void copyIsSavedUnderTheGivenId() {
        // given
        Store source = new Store();
        source.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(source);
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of());

        // when
        Store copy = storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        assertThat(copy.getStoreId()).isEqualTo("copy000001");
        assertThat(copy.getName()).isEqualTo("Kopia");
        verify(storesRepository).save(copy);
    }

    @Test
    void warehouseCopiesOnlyItsIdAndTheDocumentsSwitch() {
        // given — the copy page lists the cost centre and the printers under "set up again" (store.copy.scope.no.warehouse)
        WarehouseConfiguration warehouse = new WarehouseConfiguration();
        warehouse.setWarehouseId("MAG-1");
        warehouse.setCostCenterId("KC-1");
        warehouse.setDocumentsGenerationEnabled(true);
        warehouse.setPrinters(new LinkedList<>(List.of(new Printer())));
        Store source = new Store();
        source.setStoreId("store-1");
        source.setWarehouseConfiguration(warehouse);
        when(storesRepository.findById("store-1")).thenReturn(source);
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of());

        // when
        Store copy = storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        assertThat(copy.getWarehouseConfiguration().getWarehouseId()).isEqualTo("MAG-1");
        assertThat(copy.getWarehouseConfiguration().isDocumentsGenerationEnabled()).isTrue();
        assertThat(copy.getWarehouseConfiguration().getCostCenterId()).isNull();
        assertThat(copy.getWarehouseConfiguration().getPrinters()).isEmpty();
    }

    @Test
    void copyClearsPimIdOfAStoreOwnedEntry() {
        // given
        givenOneSourceProductWithPimId("s1");
        when(sourceLookup.findByPimId("s1")).thenReturn(Optional.of(entry("s1", "store-1")));

        // when
        storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        verify(productRepository).batchSave(productsCaptor.capture());
        Product copied = productsCaptor.getValue().get(0);
        assertThat(copied.getPimId()).isNull();
        assertThat(copied.getName()).isEqualTo("Karta");
        assertThat(copied.getEan()).isEqualTo("5901234567890");
    }

    @Test
    void copyKeepsPimIdOfAGlobalEntry() {
        // given
        givenOneSourceProductWithPimId("g1");
        when(sourceLookup.findByPimId("g1")).thenReturn(Optional.of(entry("g1", null)));

        // when
        storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        verify(productRepository).batchSave(productsCaptor.capture());
        assertThat(productsCaptor.getValue().get(0).getPimId()).isEqualTo("g1");
    }

    @Test
    void copyKeepsPimIdWhenTheEntryIsUnknown() {
        // given: e.g. the index is still empty right after startup
        givenOneSourceProductWithPimId("x1");
        when(sourceLookup.findByPimId("x1")).thenReturn(Optional.empty());

        // when
        storeCopyService.copyStore("store-1", "copy000001", "Kopia");

        // then
        verify(productRepository).batchSave(productsCaptor.capture());
        assertThat(productsCaptor.getValue().get(0).getPimId()).isEqualTo("x1");
    }

    private void givenOneSourceProductWithPimId(String pimId) {
        Store source = new Store();
        source.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(source);
        when(pimCatalog.forStore("store-1")).thenReturn(sourceLookup);

        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-def-1");
        definition.setName("Karty");
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(definition));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        Product product = new Product("cat-def-1");
        product.setName("Karta");
        product.setEan("5901234567890");
        product.setPimId(pimId);
        when(productRepository.findAll("cat-def-1")).thenReturn(List.of(product));
    }

    private PimEntry entry(String pimId, String ownerStoreId) {
        return new PimEntry(pimId, List.of(), "Brand", "Name", "GPU", null, true, null, null, null, ownerStoreId);
    }
}
