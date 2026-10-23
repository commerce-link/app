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

import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
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

    private final PimCatalog catalogMock = mock(PimCatalog.class);
    private PimCatalogRegistry registry;

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private Consumer<PIMEntryAddedEvent> entryAddedHandler() {
        registry = new PimCatalogRegistry(catalogMock, productRepository, productCatalogRepository);
        ArgumentCaptor<Consumer<PIMEntryAddedEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(catalogMock).onEntryAdded(captor.capture());
        return captor.getValue();
    }

    private static PIMEntryAddedEvent event() {
        return new PIMEntryAddedEvent("pim-1", List.of("e1"), List.of("m1"), null);
    }

    @Test
    void entryAddedTriggersAnIndexRefreshAfterPinning() {
        // given
        Product product = new Product();
        when(productRepository.findAllWithoutPimIdByEanOrMfn(List.of("e1"), List.of("m1"))).thenReturn(List.of(product));

        // when
        entryAddedHandler().accept(event());

        // then
        var order = inOrder(productRepository, catalogMock);
        order.verify(productRepository).save(product);
        order.verify(catalogMock, timeout(2000)).refresh();
    }

    @Test
    void burstOfEventsCoalescesIntoAtMostTwoRefreshes() throws Exception {
        // given
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(catalogMock).refresh();
        Consumer<PIMEntryAddedEvent> handler = entryAddedHandler();

        // when
        handler.accept(event());
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 9; i++) {
            handler.accept(event());
        }
        release.countDown();

        // then
        verify(catalogMock, timeout(2000).times(2)).refresh();
        Thread.sleep(200);
        verify(catalogMock, times(2)).refresh();
    }

    @Test
    void refreshFailureDoesNotFailTheEventHandler() {
        // given
        Product product = new Product();
        when(productRepository.findAllWithoutPimIdByEanOrMfn(List.of("e1"), List.of("m1"))).thenReturn(List.of(product));
        doThrow(new IllegalStateException("pim down")).when(catalogMock).refresh();

        // when
        entryAddedHandler().accept(event());

        // then
        verify(catalogMock, timeout(2000)).refresh();
        assertThat(product.getPimId()).isEqualTo("pim-1");
        verify(productRepository).save(product);
    }

    @Test
    void scheduledRefreshRunsEveryFiveMinutes() throws Exception {
        // when
        Scheduled scheduled = PimCatalogRegistry.class.getDeclaredMethod("refresh").getAnnotation(Scheduled.class);

        // then
        assertThat(scheduled.cron()).isEqualTo("0 */5 * * * ?");
    }

    @Test
    void scheduledRefreshGoesThroughTheCoalescingExecutor() {
        // given
        registry = new PimCatalogRegistry(catalogMock, productRepository, productCatalogRepository);

        // when
        registry.refresh();

        // then
        verify(catalogMock, timeout(2000)).refresh();
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
