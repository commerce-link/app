package pl.commercelink.stores;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StoreCreationService {

    private static final int MAX_ID_ATTEMPTS = 5;

    private final StoresRepository storesRepository;

    public Store createStore(CreateStoreRequest request) {
        Store store = new Store();
        store.setStoreId(generateFreeStoreId());
        store.setName(request.name());
        store.setApiKey(request.apiKey());
        store.setCreatedAt(Instant.now().toString());
        if (request.demoMetadata() != null) {
            store.setDemo(request.demoMetadata());
        }
        storesRepository.save(store);
        if (request.seeder() != null) {
            request.seeder().seed(store);
        }
        return store;
    }

    private String generateFreeStoreId() {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String storeId = UniqueIdentifierGenerator.generate();
            if (storesRepository.findById(storeId) == null) {
                return storeId;
            }
        }
        throw new IllegalStateException("Unable to generate a unique store id");
    }
}
