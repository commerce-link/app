package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2DeviceAuthorization;
import pl.commercelink.rest.client.OAuth2DeviceAuthorizationService;
import pl.commercelink.rest.client.OAuth2DeviceTokenResult;
import pl.commercelink.rest.client.OAuth2Secrets;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Map;

@Controller
public class MarketplaceDeviceAuthController {

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory marketplaceProviderFactory;
    private final OAuth2CredentialStore credentialStore;
    private final OAuth2DeviceAuthorizationService deviceAuthorizationService;

    @Autowired
    public MarketplaceDeviceAuthController(StoresRepository storesRepository,
                                           MarketplaceProviderFactory marketplaceProviderFactory,
                                           OAuth2CredentialStore credentialStore) {
        this(storesRepository, marketplaceProviderFactory, credentialStore, new OAuth2DeviceAuthorizationService());
    }

    MarketplaceDeviceAuthController(StoresRepository storesRepository,
                                    MarketplaceProviderFactory marketplaceProviderFactory,
                                    OAuth2CredentialStore credentialStore,
                                    OAuth2DeviceAuthorizationService deviceAuthorizationService) {
        this.storesRepository = storesRepository;
        this.marketplaceProviderFactory = marketplaceProviderFactory;
        this.credentialStore = credentialStore;
        this.deviceAuthorizationService = deviceAuthorizationService;
    }

    @PostMapping("/dashboard/store/integrations/device-auth/start")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startDeviceAuthorization(@RequestParam String providerName) {
        AuthConfig.OAuth2 oauth2 = deviceFlowConfig(providerName);
        if (oauth2 == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Provider does not support device authorization: " + providerName));
        }
        OAuth2Secrets secrets = savedSecrets(providerName);
        if (secrets == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Save clientId and clientSecret before connecting"));
        }
        OAuth2DeviceAuthorization authorization = deviceAuthorizationService.startDeviceAuthorization(
                oauth2.deviceAuthUrl(), secrets.getClientId(), secrets.getClientSecret());
        return ResponseEntity.ok(Map.of(
                "deviceCode", authorization.getDeviceCode(),
                "userCode", authorization.getUserCode(),
                "verificationUri", authorization.getVerificationUri(),
                "verificationUriComplete", authorization.getVerificationUriComplete(),
                "expiresIn", authorization.getExpiresIn(),
                "interval", authorization.getInterval()));
    }

    @PostMapping("/dashboard/store/integrations/device-auth/poll")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pollDeviceAuthorization(@RequestParam String providerName,
                                                                       @RequestParam String deviceCode) {
        AuthConfig.OAuth2 oauth2 = deviceFlowConfig(providerName);
        if (oauth2 == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Provider does not support device authorization: " + providerName));
        }
        OAuth2Secrets secrets = savedSecrets(providerName);
        if (secrets == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Save clientId and clientSecret before connecting"));
        }
        String tokenUrl = ProviderFactory.resolveAuthEndpoint(oauth2.apiUrl(), oauth2.authEndpointPath());
        OAuth2DeviceTokenResult result = deviceAuthorizationService.pollDeviceToken(
                tokenUrl, secrets.getClientId(), secrets.getClientSecret(), deviceCode);

        if (result.status() == OAuth2DeviceTokenResult.Status.AUTHORIZED) {
            connectIntegration(providerName, result.refreshToken());
            return ResponseEntity.ok(Map.of("status", "AUTHORIZED"));
        }
        if (result.status() == OAuth2DeviceTokenResult.Status.FAILED) {
            return ResponseEntity.ok(Map.of("status", "FAILED",
                    "error", result.error() != null ? result.error() : "authorization failed"));
        }
        return ResponseEntity.ok(Map.of("status", result.status().name()));
    }

    private void connectIntegration(String providerName, String refreshToken) {
        Store store = storesRepository.findById(CustomSecurityContext.getStoreId());
        marketplaceProviderFactory.seedRefreshToken(store, providerName, refreshToken);
        MarketplaceIntegration integration = store.getMarketplaceIntegration(providerName);
        if (integration == null) {
            store.getMarketplaces().add(new MarketplaceIntegration(providerName));
        } else {
            store.markConnectionAsRestored(providerName);
        }
        storesRepository.save(store);
    }

    private AuthConfig.OAuth2 deviceFlowConfig(String providerName) {
        MarketplaceProviderDescriptor descriptor = marketplaceProviderFactory.getDescriptor(providerName);
        if (descriptor == null
                || !(descriptor.authConfig() instanceof AuthConfig.OAuth2 oauth2)
                || oauth2.deviceAuthUrl() == null) {
            return null;
        }
        return oauth2;
    }

    private OAuth2Secrets savedSecrets(String providerName) {
        String credentialName = marketplaceProviderFactory.resolveCredentialName(providerName);
        OAuth2Secrets secrets;
        try {
            secrets = credentialStore.getSecrets(CustomSecurityContext.getStoreId(), credentialName);
        } catch (RuntimeException e) {
            return null;
        }
        if (secrets == null || StringUtils.isBlank(secrets.getClientId()) || StringUtils.isBlank(secrets.getClientSecret())) {
            return null;
        }
        return secrets;
    }
}
