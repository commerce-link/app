package pl.commercelink.starter.security.tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.util.ApiKeyGenerator;
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
    @Mock
    private LegacyApiKeyAttemptLimiter legacyAttemptLimiter;
    @InjectMocks
    private DefaultApiKeyValidator validator;

    @Test
    void acceptsCorrectHashedKey() {
        // given
        String apiKey = ApiKeyGenerator.generate();
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKeyHash(ApiKeyGenerator.hash(apiKey));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertTrue(validator.isValid(apiKey, STORE_ID));
    }

    @Test
    void rejectsWrongHashedKey() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKeyHash(ApiKeyGenerator.hash(ApiKeyGenerator.generate()));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertFalse(validator.isValid("some-other-key", STORE_ID));
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
        assertFalse(validator.isValid("key", " "));
        verify(storesRepository, never()).findById(" ");
    }

    @Test
    void rejectsUnknownStore() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertFalse(validator.isValid("key", STORE_ID));
    }

    @Test
    void rejectsStoreWithoutAnyKey() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertFalse(validator.isValid("key", STORE_ID));
    }

    @Test
    void acceptsLegacyLastSixCharactersWhenNoHashPresent() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertTrue(validator.isValid("prefix-abcdef", STORE_ID));
    }

    @Test
    void rejectsLegacyKeyWhenLastSixDoNotMatch() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertFalse(validator.isValid("prefix-zzzzzz", STORE_ID));
    }

    @Test
    void recordsFailureOnWrongLegacyKey() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        boolean valid = validator.isValid("prefix-zzzzzz", STORE_ID);

        // then
        assertFalse(valid);
        verify(legacyAttemptLimiter).recordFailure(STORE_ID);
    }

    @Test
    void resetsLimiterWhenLegacyKeyMatches() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        boolean valid = validator.isValid("prefix-abcdef", STORE_ID);

        // then
        assertTrue(valid);
        verify(legacyAttemptLimiter).reset(STORE_ID);
    }

    @Test
    void rejectsLegacyKeyWhenLimiterBlocksEvenIfKeyIsCorrect() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(legacyAttemptLimiter.isBlocked(STORE_ID)).thenReturn(true);

        // when
        boolean valid = validator.isValid("prefix-abcdef", STORE_ID);

        // then
        assertFalse(valid);
        verify(legacyAttemptLimiter, never()).recordFailure(STORE_ID);
    }

    @Test
    void prefersHashOverLegacyKey() {
        // given
        String apiKey = ApiKeyGenerator.generate();
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setApiKeyHash(ApiKeyGenerator.hash(apiKey));
        store.setApiKey("abcdef");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertFalse(validator.isValid("prefix-abcdef", STORE_ID));
        assertTrue(validator.isValid(apiKey, STORE_ID));
    }
}
