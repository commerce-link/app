package pl.commercelink.stores;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.util.ApiKeyGenerator;

@Service
@RequiredArgsConstructor
public class StoreApiKeyService {

    private final StoresRepository storesRepository;

    // Returns the new plaintext key once; only its hash is persisted and any legacy plaintext key is cleared.
    public String regenerate(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new IllegalArgumentException("Store not found: " + storeId);
        }

        String apiKey = ApiKeyGenerator.generate();
        store.setApiKeyHash(ApiKeyGenerator.hash(apiKey));
        store.setApiKey(null);
        storesRepository.save(store);
        return apiKey;
    }
}
