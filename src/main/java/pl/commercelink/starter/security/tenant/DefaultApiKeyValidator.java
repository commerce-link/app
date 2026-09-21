package pl.commercelink.starter.security.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.util.ApiKeyGenerator;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultApiKeyValidator implements ApiKeyValidator {

    private final StoresRepository storesRepository;
    private final LegacyApiKeyAttemptLimiter legacyAttemptLimiter;

    @Override
    public boolean isValid(String apiKey, String storeIdFromPath) {
        if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(storeIdFromPath)) {
            return false;
        }

        Store store = storesRepository.findById(storeIdFromPath);
        if (store == null) {
            return false;
        }

        String storedHash = store.getApiKeyHash();
        if (StringUtils.isNotBlank(storedHash)) {
            return constantTimeEquals(ApiKeyGenerator.hash(apiKey), storedHash);
        }

        return validLegacyKey(store, apiKey);
    }

    // Legacy stores authenticate on the last 6 plaintext characters of the key. Kept only until
    // every store is rotated to a hashed key; remove together with Store.apiKey and the attempt
    // limiter once migration completes.
    private boolean validLegacyKey(Store store, String apiKey) {
        String storeId = store.getStoreId();
        if (legacyAttemptLimiter.isBlocked(storeId)) {
            log.warn("Legacy API key attempts for store {} are temporarily blocked after repeated failures", storeId);
            return false;
        }

        String legacyKey = store.getApiKey();
        if (StringUtils.isBlank(legacyKey)) {
            return false;
        }

        boolean valid = constantTimeEquals(StringUtils.substring(apiKey, -6), legacyKey);
        if (valid) {
            legacyAttemptLimiter.reset(storeId);
            log.warn("Store {} authenticated with a legacy plaintext API key; rotate it to a hashed key", storeId);
        } else {
            legacyAttemptLimiter.recordFailure(storeId);
        }
        return valid;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
