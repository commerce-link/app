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
        cognitoUserService = new CognitoUserService(cognitoClient, POOL_ID);
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
    void createsAdminUserWithStoreAttributesAndEmailInvite() {
        // given
        ArgumentCaptor<AdminCreateUserRequest> captor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);

        // when
        cognitoUserService.createStoreAdmin("user@example.com", "abc123def4");

        // then
        verify(cognitoClient).adminCreateUser(captor.capture());
        AdminCreateUserRequest request = captor.getValue();
        Map<String, String> attributes = request.userAttributes().stream()
                .collect(Collectors.toMap(AttributeType::name, AttributeType::value));
        assertEquals(POOL_ID, request.userPoolId());
        assertEquals("user@example.com", request.username());
        assertEquals("ADMIN", attributes.get("custom:role"));
        assertEquals("abc123def4", attributes.get("custom:storeId"));
        assertEquals("true", attributes.get("email_verified"));
        assertEquals("user@example.com", attributes.get("name"));
        assertNull(request.messageAction());
    }

    @Test
    void createsUserWithPermanentPasswordAndSuppressedInvite() {
        // given
        ArgumentCaptor<AdminCreateUserRequest> createCaptor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        ArgumentCaptor<AdminSetUserPasswordRequest> passwordCaptor = ArgumentCaptor.forClass(AdminSetUserPasswordRequest.class);

        // when
        cognitoUserService.createStoreAdmin("user@example.com", "abc123def4", "Demo1!secret");

        // then
        verify(cognitoClient).adminCreateUser(createCaptor.capture());
        verify(cognitoClient).adminSetUserPassword(passwordCaptor.capture());
        assertEquals(MessageActionType.SUPPRESS, createCaptor.getValue().messageAction());
        assertEquals("Demo1!secret", passwordCaptor.getValue().password());
        assertTrue(passwordCaptor.getValue().permanent());
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
