package pl.commercelink.provider;

import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.rest.client.ConfigurableOAuth2AuthorizationService;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2RefreshToken;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.rest.client.RestApi;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;
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

    public ProviderFactory(Class<D> descriptorClass, IntegrationType integrationType,
            ProviderConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        this.integrationType = integrationType;
        for (D descriptor : ServiceLoader.load(descriptorClass)) {
            descriptors.put(descriptor.name(), descriptor);
        }
    }

    public ProviderFactory(Class<D> descriptorClass, ProviderConfigurationManager configurationManager) {
        this(descriptorClass, null, configurationManager);
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

    void registerDescriptor(D descriptor) {
        descriptors.put(descriptor.name(), descriptor);
    }

    public T get(Store store) {
        return get(store, store.getConfigurationValue(integrationType));
    }

    public T get(Store store, String providerName) {
        D descriptor = getDescriptor(providerName);
        if (descriptor == null) {
            return null;
        }
        Map<String, String> config = loadConfiguration(store, providerName);
        Map<String, Object> context = buildContext(store, descriptor);
        return descriptor.create(config, context);
    }

    protected Map<String, Object> buildContext(Store store, D descriptor) {
        return switch (descriptor.authConfig()) {
            case AuthConfig.None none -> Map.of();
            case AuthConfig.OAuth2 oauth2 -> buildOAuth2Context(store, descriptor, oauth2);
        };
    }

    private Map<String, Object> buildOAuth2Context(
            Store store, D descriptor, AuthConfig.OAuth2 oauth2) {
        String apiUrl = oauth2.apiUrl();
        String credentialName = resolveCredentialName(descriptor);

        ConfigurableOAuth2AuthorizationService authService = new ConfigurableOAuth2AuthorizationService(
                credentialStore, tokenStore,
                credentialName,
                resolveAuthEndpoint(apiUrl, oauth2.authEndpointPath()),
                resolveAuthEndpoint(apiUrl, oauth2.refreshEndpointPath()),
                oauth2.refreshTokenExpirationSeconds(),
                storeId -> {
                    Store s = storesRepository.findById(storeId);
                    onAuthorizationLost(s, descriptor);
                    storesRepository.save(s);
                });

        RestApi.Builder restApiBuilder = RestApi.builder(apiUrl);
        oauth2DefaultHeaders(oauth2).forEach(restApiBuilder::defaultHeader);

        RestApiWithRetry restApiWithRetry = new RestApiWithRetry(
                restApiBuilder.build(),
                () -> authService.getAccessToken(store.getStoreId()));

        return Map.of("restApi", restApiWithRetry);
    }

    static String resolveAuthEndpoint(String apiUrl, String path) {
        return path.startsWith("http") ? path : apiUrl + path;
    }

    static Map<String, String> oauth2DefaultHeaders(AuthConfig.OAuth2 oauth2) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (oauth2.acceptHeader() != null) {
            headers.put("Accept", oauth2.acceptHeader());
        }
        if (oauth2.contentTypeHeader() != null) {
            headers.put("Content-Type", oauth2.contentTypeHeader());
        }
        return headers;
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

        if (descriptor.authConfig() instanceof AuthConfig.OAuth2
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
            boolean persisted = configurationManager.saveConfiguration(store, configName, descriptor, configuration);
            if (persisted) {
                seedRefreshToken(store, descriptor, configName, configuration);
            }
        }
    }

    private void seedRefreshToken(Store store, D descriptor, String configName, Map<String, String> configuration) {
        if (!(descriptor.authConfig() instanceof AuthConfig.OAuth2 oauth2) || tokenStore == null) {
            return;
        }
        String fieldKey = oauth2.refreshTokenFieldKey();
        if (fieldKey == null) {
            return;
        }
        String refreshToken = configuration.get(fieldKey);
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        OAuth2RefreshToken token = new OAuth2RefreshToken(
                refreshToken,
                Instant.ofEpochMilli(now),
                Instant.ofEpochMilli(now + oauth2.refreshTokenExpirationSeconds() * 1000));
        tokenStore.storeToken(store.getStoreId(), configName,
                ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN, token);
        tokenStore.deleteToken(store.getStoreId(), configName,
                ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN);
    }
}
