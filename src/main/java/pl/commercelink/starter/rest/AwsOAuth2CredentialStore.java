package pl.commercelink.starter.rest;

import org.springframework.stereotype.Component;
import pl.commercelink.rest.client.OAuth2CredentialStore;
import pl.commercelink.rest.client.OAuth2Secrets;
import pl.commercelink.starter.secrets.SecretsManager;

@Component
public class AwsOAuth2CredentialStore implements OAuth2CredentialStore {

    private final SecretsManager secretsManager;

    public AwsOAuth2CredentialStore(SecretsManager secretsManager) {
        this.secretsManager = secretsManager;
    }

    @Override
    public void createOrUpdateSecrets(String storeId, String tokenName, OAuth2Secrets secrets) {
        String secretName = getSecretName(storeId, tokenName);
        if (secretsManager.exists(secretName)) {
            secretsManager.updateSecret(secretName, secrets);
        } else {
            secretsManager.createSecret(secretName, secrets);
        }
    }

    @Override
    public OAuth2Secrets getSecrets(String storeId, String tokenName) {
        return secretsManager.getSecret(getSecretName(storeId, tokenName), OAuth2Secrets.class);
    }

    @Override
    public void deleteSecrets(String storeId, String tokenName) {
        String secretName = getSecretName(storeId, tokenName);
        if (secretsManager.exists(secretName)) {
            secretsManager.deleteSecret(secretName);
        }
    }

    private String getSecretName(String storeId, String tokenName) {
        return storeId + "-" + tokenName;
    }

}
