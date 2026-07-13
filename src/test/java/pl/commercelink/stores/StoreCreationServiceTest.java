package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreCreationServiceTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSeeder seeder;
    @InjectMocks
    private StoreCreationService service;

    @Test
    void createsBareStoreWithGeneratedIdAndCreatedAt() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", "key-1"));

        // then
        assertNotNull(store.getStoreId());
        assertEquals(10, store.getStoreId().length());
        assertEquals("Sklep X", store.getName());
        assertEquals("key-1", store.getApiKey());
        assertNotNull(store.getCreatedAt());
        assertNull(store.getDemo());
        verify(storesRepository).save(store);
    }

    @Test
    void retriesOnStoreIdCollision() {
        // given
        when(storesRepository.findById(anyString()))
                .thenReturn(new Store())
                .thenReturn(null);

        // when
        Store store = service.createStore(CreateStoreRequest.bare("Sklep X", null));

        // then
        assertNotNull(store.getStoreId());
        verify(storesRepository, times(2)).findById(anyString());
    }

    @Test
    void failsAfterExhaustedCollisionRetries() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(new Store());

        // when / then
        assertThrows(IllegalStateException.class,
                () -> service.createStore(CreateStoreRequest.bare("Sklep X", null)));
    }

    @Test
    void appliesDemoMetadataAndDelegatesToSeeder() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);
        DemoStoreMetadata demo = new DemoStoreMetadata("a@b.pl", "2026-07-13T10:00:00Z", "2026-07-16T10:00:00Z");

        // when
        Store store = service.createStore(CreateStoreRequest.seeded("Sklep demo", demo, seeder));

        // then
        assertSame(demo, store.getDemo());
        verify(storesRepository).save(store);
        verify(seeder).seed(store);
    }

    @Test
    void skipsSeederWhenAbsent() {
        // given
        when(storesRepository.findById(anyString())).thenReturn(null);

        // when
        service.createStore(CreateStoreRequest.bare("Sklep X", null));

        // then
        verifyNoInteractions(seeder);
    }
}
