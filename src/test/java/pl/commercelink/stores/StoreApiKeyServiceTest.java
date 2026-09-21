package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.util.ApiKeyGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreApiKeyServiceTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @InjectMocks
    private StoreApiKeyService service;

    @Test
    void storesHashAndClearsLegacyKeyReturningPlaintextOnce() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        String apiKey = service.regenerate(STORE_ID);

        // then
        assertNotNull(apiKey);
        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(captor.capture());
        Store saved = captor.getValue();
        assertNull(saved.getApiKey());
        assertEquals(ApiKeyGenerator.hash(apiKey), saved.getApiKeyHash());
    }

    @Test
    void throwsAndDoesNotSaveWhenStoreMissing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertThrows(IllegalArgumentException.class, () -> service.regenerate(STORE_ID));
        verify(storesRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
