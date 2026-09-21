package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2DeviceAuthorization;
import pl.commercelink.rest.client.OAuth2DeviceAuthorizationService;
import pl.commercelink.rest.client.OAuth2DeviceTokenResult;
import pl.commercelink.rest.client.OAuth2Secrets;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import java.io.Serializable;
import java.time.Instant;

/**
 * Connects a marketplace account through the OAuth 2.0 device flow (Allegro): the marketplace issues a code, the
 * operator confirms it on the marketplace's own page, and polling the token endpoint returns the refresh token the
 * store keeps. Works on the store it is given, so a super admin connects the store named in the address.
 */
@Component
class MarketplaceAuthorization {

    private final StoresRepository storesRepository;
    private final MarketplaceProviderFactory marketplaceProviderFactory;
    private final OAuth2CredentialStore credentialStore;
    private final StoreNotificationService notificationService;
    private final OAuth2DeviceAuthorizationService deviceAuthorizationService;

    @Autowired
    MarketplaceAuthorization(StoresRepository storesRepository, MarketplaceProviderFactory marketplaceProviderFactory,
                             OAuth2CredentialStore credentialStore, StoreNotificationService notificationService) {
        this(storesRepository, marketplaceProviderFactory, credentialStore, notificationService,
                new OAuth2DeviceAuthorizationService());
    }

    MarketplaceAuthorization(StoresRepository storesRepository, MarketplaceProviderFactory marketplaceProviderFactory,
                             OAuth2CredentialStore credentialStore, StoreNotificationService notificationService,
                             OAuth2DeviceAuthorizationService deviceAuthorizationService) {
        this.storesRepository = storesRepository;
        this.marketplaceProviderFactory = marketplaceProviderFactory;
        this.credentialStore = credentialStore;
        this.notificationService = notificationService;
        this.deviceAuthorizationService = deviceAuthorizationService;
    }

    boolean supports(String marketplace) {
        return deviceFlowConfig(marketplace) != null;
    }

    /**
     * Asks the marketplace for a code the operator confirms on its page.
     *
     * @throws AuthorizationException with the message key of the reason when the flow cannot start
     */
    Pending start(Store store, String marketplace) {
        AuthConfig.OAuth2 oauth2 = deviceFlowConfig(marketplace);
        if (oauth2 == null) {
            throw new AuthorizationException("store.marketplace.authorize.error.unsupported");
        }
        OAuth2Secrets secrets = savedSecrets(store.getStoreId(), marketplace);
        if (secrets == null) {
            throw new AuthorizationException("store.marketplace.authorize.error.noCredentials");
        }
        OAuth2DeviceAuthorization authorization;
        try {
            authorization = deviceAuthorizationService.startDeviceAuthorization(
                    oauth2.deviceAuthUrl(), secrets.getClientId(), secrets.getClientSecret());
        } catch (HttpClientException e) {
            // Allegro answers 400/401 for a wrong client id or secret
            throw new AuthorizationException("store.marketplace.authorize.error.rejected");
        }
        long interval = authorization.getInterval() > 0 ? authorization.getInterval() : 5;
        return new Pending(store.getStoreId(), marketplace, authorization.getDeviceCode(), authorization.getUserCode(),
                authorization.getVerificationUri(), authorization.getVerificationUriComplete(),
                Instant.now().plusSeconds(authorization.getExpiresIn()), interval);
    }

    /** One look at the token endpoint; on success the refresh token is stored and the integration marked connected. */
    OAuth2DeviceTokenResult.Status check(Store store, Pending pending) {
        AuthConfig.OAuth2 oauth2 = deviceFlowConfig(pending.marketplace());
        OAuth2Secrets secrets = oauth2 == null ? null : savedSecrets(store.getStoreId(), pending.marketplace());
        if (secrets == null) {
            return OAuth2DeviceTokenResult.Status.FAILED;
        }
        String tokenUrl = ProviderFactory.resolveAuthEndpoint(oauth2.apiUrl(), oauth2.authEndpointPath());
        OAuth2DeviceTokenResult result = deviceAuthorizationService.pollDeviceToken(
                tokenUrl, secrets.getClientId(), secrets.getClientSecret(), pending.deviceCode());
        if (result.status() == OAuth2DeviceTokenResult.Status.AUTHORIZED) {
            connect(store, pending.marketplace(), result.refreshToken());
        }
        return result.status();
    }

    private void connect(Store store, String marketplace, String refreshToken) {
        marketplaceProviderFactory.seedRefreshToken(store, marketplace, refreshToken);
        MarketplaceIntegration integration = store.getMarketplaceIntegration(marketplace);
        if (integration == null) {
            store.getMarketplaces().add(new MarketplaceIntegration(marketplace));
        } else {
            store.markConnectionAsRestored(marketplace);
        }
        storesRepository.save(store);
        notificationService.resolve(store.getStoreId(), StoreNotificationType.UNAUTHENTICATED,
                StoreNotification.marketplaceConnectionObject(marketplace));
    }

    private AuthConfig.OAuth2 deviceFlowConfig(String marketplace) {
        MarketplaceProviderDescriptor descriptor = marketplaceProviderFactory.getDescriptor(marketplace);
        if (descriptor == null
                || !(descriptor.authConfig() instanceof AuthConfig.OAuth2 oauth2)
                || oauth2.deviceAuthUrl() == null) {
            return null;
        }
        return oauth2;
    }

    private OAuth2Secrets savedSecrets(String storeId, String marketplace) {
        String credentialName = marketplaceProviderFactory.resolveCredentialName(marketplace);
        OAuth2Secrets secrets;
        try {
            secrets = credentialStore.getSecrets(storeId, credentialName);
        } catch (RuntimeException e) {
            return null;
        }
        if (secrets == null || StringUtils.isBlank(secrets.getClientId()) || StringUtils.isBlank(secrets.getClientSecret())) {
            return null;
        }
        return secrets;
    }

    /** A code waiting for the operator's confirmation, kept in the HTTP session between the page and its checks. */
    record Pending(String storeId, String marketplace, String deviceCode, String userCode, String verificationUri,
                   String verificationUriComplete, Instant expiresAt, long intervalSeconds) implements Serializable {

        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }

        boolean belongsTo(String storeId, String marketplace) {
            return this.storeId.equals(storeId) && this.marketplace.equals(marketplace);
        }

        /** The address with the code filled in, or the plain one when the marketplace gives none. */
        String openUri() {
            return verificationUriComplete != null ? verificationUriComplete : verificationUri;
        }
    }

    static class AuthorizationException extends RuntimeException {

        private final String messageKey;

        AuthorizationException(String messageKey) {
            super(messageKey);
            this.messageKey = messageKey;
        }

        String messageKey() {
            return messageKey;
        }
    }
}
