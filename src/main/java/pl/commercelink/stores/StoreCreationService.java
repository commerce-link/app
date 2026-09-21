package pl.commercelink.stores;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.starter.util.ApiKeyGenerator;
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
        String apiKey = ApiKeyGenerator.generate();
        store.setApiKeyHash(ApiKeyGenerator.hash(apiKey));
        store.setCreatedAt(Instant.now().toString());
        if (StringUtils.isNotBlank(request.ownerEmail())) {
            BillingDetails billingDetails = new BillingDetails();
            billingDetails.setEmail(request.ownerEmail());
            store.setBillingDetails(billingDetails);
        }
        if (request.demoMetadata() != null) {
            store.setDemo(request.demoMetadata());
        }
        storesRepository.save(store);
        if (request.seeder() != null) {
            try {
                request.seeder().seed(store);
                storesRepository.save(store);
            } catch (RuntimeException e) {
                throw new StoreSeedingException(store.getStoreId(), e);
            }
        }
        // Set the transient plaintext only after every save so it is never persisted.
        store.setPlaintextApiKey(apiKey);
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
