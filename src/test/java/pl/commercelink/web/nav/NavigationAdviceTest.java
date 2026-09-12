package pl.commercelink.web.nav;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigationAdviceTest {

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private NavigationAdvice advice;

    private void loggedInAs(String role) {
        loggedInAs(role, Map.of("sub", "user-1"));
    }

    private void loggedInAs(String role, String email) {
        loggedInAs(role, Map.of("sub", "user-1", "email", email));
    }

    private void loggedInAs(String role, Map<String, Object> attributes) {
        CustomUser user = new CustomUser(
                new DefaultOAuth2User(List.of(), attributes, "sub"), null, Map.of("role", role));
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private HttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildsTheNavigationForTheLoggedInRoleAndPath() {
        // given
        loggedInAs("ADMIN");

        // when
        NavigationModel model = advice.navigation(request("/dashboard/deliveries"));

        // then
        assertThat(model).isNotNull();
        assertThat(model.active().key()).isEqualTo("deliveries");
    }

    @Test
    void buildsNoNavigationWhenNobodyIsLoggedIn() {
        // when
        NavigationModel model = advice.navigation(request("/dashboard/orders"));

        // then
        assertThat(model).isNull();
    }

    @Test
    void namesTheStoreTheSuperAdminIsCurrentlyWorkingIn() {
        // given
        loggedInAs("SUPER_ADMIN");
        Store store = new Store();
        store.setStoreId("uma2dqukxr");
        store.setName("Bio Planet");
        when(storesRepository.findById("uma2dqukxr")).thenReturn(store);

        // when
        StoreContext context = advice.storeContext(request("/dashboard/store/uma2dqukxr/deliveries"));

        // then
        assertThat(context).isEqualTo(new StoreContext("uma2dqukxr", "Bio Planet"));
    }

    @Test
    void doesNotTouchTheRepositoryOnPathsWithoutAStoreId() {
        // given
        loggedInAs("SUPER_ADMIN");

        // when
        StoreContext context = advice.storeContext(request("/dashboard/store/rma-centers"));

        // then
        assertThat(context).isNull();
        verifyNoInteractions(storesRepository);
    }

    @Test
    void leavesTheStoreChipOffForRolesOtherThanSuperAdmin() {
        // given
        loggedInAs("ADMIN");

        // when
        StoreContext context = advice.storeContext(request("/dashboard/store/uma2dqukxr/deliveries"));

        // then
        assertThat(context).isNull();
        verifyNoInteractions(storesRepository);
    }

    @Test
    void returnsTheLoggedInUsersEmailAddress() {
        // given
        loggedInAs("ADMIN", "operator@commercelink.local");

        // when / then
        assertThat(advice.userEmail()).isEqualTo("operator@commercelink.local");
    }

    @Test
    void returnsNoEmailWhenNobodyIsLoggedIn() {
        // when / then
        assertThat(advice.userEmail()).isNull();
    }
}
