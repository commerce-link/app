package pl.commercelink.provider;

import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.rest.client.ConfigurableOAuth2AuthorizationService;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.rest.client.RestApi;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class ProviderFactory<D extends ProviderDescriptor<T>, T> {

    private final Map<String, D> descriptors = new LinkedHashMap<>();
    private final ProviderConfigurationManager configurationManager;
    private final IntegrationType integrationType;
    private OAuth2CredentialStore credentialStore;
    private OAuth2TokenStore tokenStore;
    private StoresRepository storesRepository;

    public ProviderFactory(Class<D> descriptorClass, IntegrationType integrationType, ProviderConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        this.integrationType = integrationType;
        for (D descriptor : ServiceLoader.load(descriptorClass)) {
            descriptors.put(descriptor.name(), descriptor);
        }
    }

    protected ProviderFactory(Class<D> descriptorClass, IntegrationType integrationType,
                              ProviderConfigurationManager configurationManager,
                              OAuth2CredentialStore credentialStore, OAuth2TokenStore tokenStore,
                              StoresRepository storesRepository) {
        this(descriptorClass, integrationType, configurationManager);
        this.credentialStore = credentialStore;
        this.tokenStore = tokenStore;
        this.storesRepository = storesRepository;
    }

    protected ProviderFactory(Class<D> descriptorClass, ProviderConfigurationManager configurationManager,
                              OAuth2CredentialStore credentialStore, OAuth2TokenStore tokenStore,
                              StoresRepository storesRepository) {
        this(descriptorClass, null, configurationManager, credentialStore, tokenStore, storesRepository);
    }

    public T get(Store store) {
        String providerName = store.getConfigurationValue(integrationType);
        D descriptor = descriptors.get(providerName);
        if (descriptor == null) {
            return null;
        }
        String configName = resolveCredentialName(descriptor);
        Map<String, String> config = configurationManager.loadConfiguration(store, configName);
        Map<String, Object> context = buildContext(store, descriptor, config);
        return descriptor.create(config, context);
    }

    protected Map<String, Object> buildContext(Store store, D descriptor, Map<String, String> config) {
        Map<String, String> metadata = descriptor.metadata();
        if (!"oauth2".equals(metadata.get("authType"))) {
            return Map.of();
        }

        String apiUrl = config.containsKey("apiUrl") ? config.get("apiUrl") : metadata.get("apiUrl");
        String credentialName = resolveCredentialName(descriptor);

        ConfigurableOAuth2AuthorizationService authService = new ConfigurableOAuth2AuthorizationService(
                credentialStore, tokenStore,
                credentialName,
                apiUrl + metadata.get("authEndpointPath"),
                apiUrl + metadata.get("refreshEndpointPath"),
                Long.parseLong(metadata.get("refreshTokenExpirationSeconds")),
                storeId -> {
                    Store s = storesRepository.findById(storeId);
                    onAuthorizationLost(s, descriptor);
                    storesRepository.save(s);
                }
        );

        RestApi.Builder restApiBuilder = RestApi.builder(apiUrl);
        String acceptHeader = metadata.get("acceptHeader");
        if (acceptHeader != null) {
            restApiBuilder.defaultHeader("Accept", acceptHeader);
        }

        RestApiWithRetry restApiWithRetry = new RestApiWithRetry(
                restApiBuilder.build(),
                () -> authService.getAccessToken(store.getStoreId())
        );

        return Map.of("restApi", restApiWithRetry);
    }

    protected void onAuthorizationLost(Store store, D descriptor) {
    }

    protected String resolveCredentialName(D descriptor) {
        return descriptor.name();
    }

    public D getDescriptor(String name) {
        return descriptors.get(name);
    }

    public Map<String, String> loadConfiguration(Store store, String providerName) {
        D descriptor = descriptors.get(providerName);
        String configName = descriptor != null ? resolveCredentialName(descriptor) : providerName;
        return configurationManager.loadConfiguration(store, configName);
    }

    public Collection<D> availableProviders() {
        return descriptors.values();
    }

    public Map<String, String> loadConfigurationForUI(Store store) {
        String providerName = store.getConfigurationValue(integrationType);
        D descriptor = descriptors.get(providerName);
        if (descriptor == null) {
            return new HashMap<>();
        }
        String configName = resolveCredentialName(descriptor);
        return configurationManager.getConfigurationForUI(store, configName, descriptor);
    }

    public void deleteConfiguration(Store store, String providerName) {
        D descriptor = descriptors.get(providerName);
        if (descriptor == null) {
            return;
        }
        String configName = resolveCredentialName(descriptor);
        configurationManager.deleteConfiguration(store, configName);

        if ("oauth2".equals(descriptor.metadata().get("authType"))
                && credentialStore != null && tokenStore != null) {
            credentialStore.deleteSecrets(store.getStoreId(), configName);
            tokenStore.deleteToken(store.getStoreId(), configName, "access_token");
            tokenStore.deleteToken(store.getStoreId(), configName, "refresh_token");
        }
    }

    public void saveConfiguration(Store store, String providerName, Map<String, String> configuration) {
        D descriptor = descriptors.get(providerName);
        if (descriptor != null && configuration != null) {
            String configName = resolveCredentialName(descriptor);
            configurationManager.saveConfiguration(store, configName, descriptor, configuration);
        }
    }
}
