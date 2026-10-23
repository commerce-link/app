package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The RMA list is a store's list: the super admin has no store in the session and is refused on the page and on the
 * results fragment, the store's users are let in. standaloneSetup MockMvc does not evaluate PreAuthorize, so the
 * handlers' own rules are evaluated here as method security does (a denial becomes a 403).
 */
class RmaListAuthorizationTest {

    private static boolean granted(String handler, String role) throws Exception {
        Method method = RmaListController.class.getMethod(handler, MultiValueMap.class, Locale.class, org.springframework.ui.Model.class);
        Object[] args = {new LinkedMultiValueMap<String, String>(), Locale.ENGLISH, new ExtendedModelMap()};
        SimpleMethodInvocation call = new SimpleMethodInvocation(mock(RmaListController.class), method, args);
        Authentication user = new TestingAuthenticationToken("u", "x", "ROLE_" + role);
        return new PreAuthorizeAuthorizationManager().authorize(() -> user, call).isGranted();
    }

    @Test
    void thePageAndTheFragmentRefuseTheSuperAdminAndLetTheStoreUsersIn() throws Exception {
        for (String handler : new String[]{"list", "results"}) {
            // when / then
            assertThat(granted(handler, "SUPER_ADMIN")).as(handler + " SUPER_ADMIN").isFalse();
            assertThat(granted(handler, "ADMIN")).as(handler + " ADMIN").isTrue();
            assertThat(granted(handler, "USER")).as(handler + " USER").isTrue();
        }
    }
}
