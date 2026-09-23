package pl.commercelink.starter.security.tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultApiKeyValidatorTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @InjectMocks
    private DefaultApiKeyValidator validator;

    @Test
    void acceptsKeyWhoseLastSixCharactersMatchTheStoredKey() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertTrue(validator.isValid("prefix-abcdef", STORE_ID));
    }

    @Test
    void acceptsKeyThatIsExactlyTheStoredSixCharacters() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertTrue(validator.isValid("abcdef", STORE_ID));
    }

    @Test
    void rejectsKeyWhoseLastSixCharactersDoNotMatch() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertFalse(validator.isValid("prefix-zzzzzz", STORE_ID));
    }

    @Test
    void rejectsWhenStoreDoesNotExist() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertFalse(validator.isValid("prefix-abcdef", STORE_ID));
    }

    @Test
    void rejectsWhenStoreHasNoKey() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertFalse(validator.isValid("prefix-abcdef", STORE_ID));
    }

    @Test
    void rejectsBlankApiKeyWithoutTouchingRepository() {
        // when / then
        assertFalse(validator.isValid(" ", STORE_ID));
        verify(storesRepository, never()).findById(STORE_ID);
    }

    @Test
    void rejectsBlankStoreIdWithoutTouchingRepository() {
        // when / then
        assertFalse(validator.isValid("prefix-abcdef", " "));
        verify(storesRepository, never()).findById(" ");
    }
}
