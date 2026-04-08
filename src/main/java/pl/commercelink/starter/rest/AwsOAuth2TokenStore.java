package pl.commercelink.starter.rest;

import org.springframework.stereotype.Component;
import pl.commercelink.rest.client.OAuth2TokenStore;
import pl.commercelink.starter.secrets.ParameterStore;

import java.util.Optional;

@Component
public class AwsOAuth2TokenStore implements OAuth2TokenStore {

    private final ParameterStore parameterStore;

    public AwsOAuth2TokenStore(ParameterStore parameterStore) {
        this.parameterStore = parameterStore;
    }

    @Override
    public <T> Optional<T> getToken(String storeId, String tokenName, String tokenType, Class<T> clazz) {
        return parameterStore.getParameter(storeId, tokenName, tokenType, clazz);
    }

    @Override
    public void storeToken(String storeId, String tokenName, String tokenType, Object token) {
        parameterStore.putParameter(storeId, tokenName, tokenType, token);
    }

    @Override
    public void deleteToken(String storeId, String tokenName, String tokenType) {
        parameterStore.deleteParameter(storeId, tokenName, tokenType);
    }

}
