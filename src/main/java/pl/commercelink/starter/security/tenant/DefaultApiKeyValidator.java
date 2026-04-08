package pl.commercelink.starter.security.tenant;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.StoresRepository;

@Component
public class DefaultApiKeyValidator implements ApiKeyValidator {

    private final StoresRepository storesRepository;

    public DefaultApiKeyValidator(StoresRepository storesRepository) {
        this.storesRepository = storesRepository;
    }

    @Override
    public boolean isValid(String apiKey, String storeIdFromPath) {
        if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(storeIdFromPath)) {
            return false;
        }

        String last6Chars = StringUtils.substring(apiKey, -6);
        String storeId = storesRepository.findByApiKey(last6Chars);

        return StringUtils.equals(storeId, storeIdFromPath);
    }
}
