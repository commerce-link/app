package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PimCatalogRegistryTest {

    private final PimCatalogDescriptor realAdapter = descriptor("commercelink-pim");
    private final PimCatalogDescriptor devAdapter = descriptor("pim-dev");

    @Test
    void failsFastWhenNoDescriptorIsPresent() {
        // when / then
        assertThatThrownBy(() -> PimCatalogRegistry.resolveDescriptor(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No PimCatalogDescriptor found on classpath");
    }

    @Test
    void resolvesTheOnlyDescriptorPresent() {
        // when
        PimCatalogDescriptor resolved = PimCatalogRegistry.resolveDescriptor(List.of(devAdapter));

        // then
        assertThat(resolved).isSameAs(devAdapter);
    }

    @Test
    void failsFastNamingBothAdaptersWhenMoreThanOneIsPresent() {
        // when / then
        assertThatThrownBy(() -> PimCatalogRegistry.resolveDescriptor(List.of(devAdapter, realAdapter)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commercelink-pim")
                .hasMessageContaining("pim-dev");
    }

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductCatalogRepository productCatalogRepository = mock(ProductCatalogRepository.class);

    @Test
    void globalEntryAttachesToProductsOfEveryStore() {
        // given
        Product product = new Product();
        when(productRepository.findAllWithoutPimIdByEanOrMfn(List.of("e1"), List.of("m1"))).thenReturn(List.of(product));

        // when
        PimCatalogRegistry.attachEntryToProducts(
                new PIMEntryAddedEvent("pim-1", List.of("e1"), List.of("m1"), null),
                productRepository, productCatalogRepository);

        // then
        assertThat(product.getPimId()).isEqualTo("pim-1");
        verify(productRepository).save(product);
        verify(productRepository, never()).findAllWithoutPimIdByEanOrMfnInCategories(any(), any(), any());
    }

    @Test
    void storeEntryAttachesOnlyToProductsInTheOwnersCategories() {
        // given
        when(productCatalogRepository.findAll("s1")).thenReturn(List.of(
                catalogWith("c1", "c2"), catalogWith("c2", "c3")));
        Product product = new Product();
        when(productRepository.findAllWithoutPimIdByEanOrMfnInCategories(
                List.of("c1", "c2", "c3"), List.of("e1"), List.of("m1"))).thenReturn(List.of(product));

        // when
        PimCatalogRegistry.attachEntryToProducts(
                new PIMEntryAddedEvent("pim-1", List.of("e1"), List.of("m1"), "s1"),
                productRepository, productCatalogRepository);

        // then
        assertThat(product.getPimId()).isEqualTo("pim-1");
        verify(productRepository).save(product);
        verify(productRepository, never()).findAllWithoutPimIdByEanOrMfn(any(), any());
    }

    @Test
    void storeWithoutCatalogsAttachesNothing() {
        // given
        when(productCatalogRepository.findAll("s1")).thenReturn(List.of());

        // when
        PimCatalogRegistry.attachEntryToProducts(
                new PIMEntryAddedEvent("pim-1", List.of("e1"), List.of("m1"), "s1"),
                productRepository, productCatalogRepository);

        // then
        verify(productRepository, never()).save(any());
        verify(productRepository, never()).findAllWithoutPimIdByEanOrMfn(any(), any());
    }

    private static ProductCatalog catalogWith(String... categoryIds) {
        ProductCatalog catalog = new ProductCatalog();
        for (String id : categoryIds) {
            CategoryDefinition category = new CategoryDefinition();
            category.setCategoryId(id);
            catalog.getCategories().add(category);
        }
        return catalog;
    }

    private static PimCatalogDescriptor descriptor(String name) {
        return new PimCatalogDescriptor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String displayName() {
                return name;
            }

            @Override
            public List<ProviderField> configurationFields() {
                return List.of();
            }

            @Override
            public PimCatalog create(Map<String, String> configuration) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
