package pl.commercelink.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.security.UserRole;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CognitoUserService {

    private static final String STORE_ID_ATTRIBUTE = "custom:storeId";

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${cognito.user-pool-id}")
    String userPoolId;

    public boolean userExists(String email) {
        try {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());
            return true;
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    public void createStoreAdmin(String email, String storeId, String permanentPassword, boolean emailVerified) {
        cognitoClient.adminCreateUser(createUserRequest(email, storeId, emailVerified)
                .messageAction(MessageActionType.SUPPRESS)
                .build());
        cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .password(permanentPassword)
                .permanent(true)
                .build());
    }

    public void deleteUser(String email) {
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());
        } catch (UserNotFoundException ignored) {
        }
    }

    /**
     * Deletes the account only while it still points at the store. When two registrations of one e-mail race, the
     * loser rolls its own store back while the account belongs to the winner's store, which must keep it.
     */
    public void deleteStoreOwner(String email, String storeId) {
        AdminGetUserResponse user;
        try {
            user = cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());
        } catch (UserNotFoundException e) {
            return;
        }
        boolean ownsStore = user.userAttributes().stream()
                .anyMatch(attribute -> STORE_ID_ATTRIBUTE.equals(attribute.name()) && storeId.equals(attribute.value()));
        if (ownsStore) {
            deleteUser(email);
        } else {
            log.warn("The owner account of store {} belongs to another store, keeping it", storeId);
        }
    }

    private AdminCreateUserRequest.Builder createUserRequest(String email, String storeId, boolean emailVerified) {
        return AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value(String.valueOf(emailVerified)).build(),
                        AttributeType.builder().name("name").value(email).build(),
                        AttributeType.builder().name("custom:role").value(UserRole.ADMIN.name()).build(),
                        AttributeType.builder().name(STORE_ID_ATTRIBUTE).value(storeId).build());
    }
}
