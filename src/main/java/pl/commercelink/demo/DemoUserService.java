package pl.commercelink.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.security.UserRole;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Service
@ConditionalOnProperty(name = "app.demo.registration.enabled", havingValue = "true")
public class DemoUserService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    public DemoUserService(CognitoIdentityProviderClient cognitoClient,
                           @Value("${cognito.user-pool-id}") String userPoolId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
    }

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

    public void createDemoAdmin(String email, String storeId) {
        cognitoClient.adminCreateUser(createUserRequest(email, storeId).build());
    }

    public void createDemoAdmin(String email, String storeId, String permanentPassword) {
        cognitoClient.adminCreateUser(createUserRequest(email, storeId)
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

    private AdminCreateUserRequest.Builder createUserRequest(String email, String storeId) {
        return AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("custom:role").value(UserRole.ADMIN.name()).build(),
                        AttributeType.builder().name("custom:storeId").value(storeId).build());
    }
}
