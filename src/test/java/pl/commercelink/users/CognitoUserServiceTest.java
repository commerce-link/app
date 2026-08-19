package pl.commercelink.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoUserServiceTest {

    private static final String POOL_ID = "eu-central-1_test";

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    private CognitoUserService cognitoUserService;

    @BeforeEach
    void setUp() {
        cognitoUserService = new CognitoUserService(cognitoClient);
        cognitoUserService.userPoolId = POOL_ID;
    }

    @Test
    void reportsMissingUserAsNotExisting() {
        // given
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("missing").build());

        // when / then
        assertFalse(cognitoUserService.userExists("user@example.com"));
    }

    @Test
    void createsAdminUserWithStoreAttributesAndSuppressedInvite() {
        // given
        ArgumentCaptor<AdminCreateUserRequest> createCaptor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        ArgumentCaptor<AdminSetUserPasswordRequest> passwordCaptor = ArgumentCaptor.forClass(AdminSetUserPasswordRequest.class);

        // when
        cognitoUserService.createStoreAdmin("user@example.com", "abc123def4", "Tajne1!haslo", true);

        // then
        verify(cognitoClient).adminCreateUser(createCaptor.capture());
        verify(cognitoClient).adminSetUserPassword(passwordCaptor.capture());
        AdminCreateUserRequest request = createCaptor.getValue();
        Map<String, String> attributes = request.userAttributes().stream()
                .collect(Collectors.toMap(AttributeType::name, AttributeType::value));
        assertEquals(POOL_ID, request.userPoolId());
        assertEquals("user@example.com", request.username());
        assertEquals("ADMIN", attributes.get("custom:role"));
        assertEquals("abc123def4", attributes.get("custom:storeId"));
        assertEquals("true", attributes.get("email_verified"));
        assertEquals("user@example.com", attributes.get("name"));
        assertEquals(MessageActionType.SUPPRESS, request.messageAction());
        assertEquals("Tajne1!haslo", passwordCaptor.getValue().password());
        assertTrue(passwordCaptor.getValue().permanent());
    }

    @Test
    void createsUnverifiedUserWhenEmailVerificationIsRequired() {
        // given
        ArgumentCaptor<AdminCreateUserRequest> createCaptor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);

        // when
        cognitoUserService.createStoreAdmin("user@example.com", "abc123def4", "Tajne1!haslo", false);

        // then
        verify(cognitoClient).adminCreateUser(createCaptor.capture());
        Map<String, String> attributes = createCaptor.getValue().userAttributes().stream()
                .collect(Collectors.toMap(AttributeType::name, AttributeType::value));
        assertEquals("false", attributes.get("email_verified"));
    }

    @Test
    void deleteUserSwallowsMissingUser() {
        // given
        when(cognitoClient.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("missing").build());

        // when / then
        assertDoesNotThrow(() -> cognitoUserService.deleteUser("user@example.com"));
    }
}
