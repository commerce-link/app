package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClarityAdviceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trackingDisabledWhenProjectIdIsEmpty() {
        // given
        logInWithStoreId("s-1");
        ClarityAdvice advice = new ClarityAdvice("");

        // when / then
        assertNull(advice.clarityProjectId());
        assertNull(advice.clarityStoreId());
    }

    @Test
    void trackingDisabledWhenProjectIdIsBlank() {
        // given
        logInWithStoreId("s-1");
        ClarityAdvice advice = new ClarityAdvice("   ");

        // when / then
        assertNull(advice.clarityProjectId());
        assertNull(advice.clarityStoreId());
    }

    @Test
    void trackingDisabledWhenProjectIdIsNull() {
        // given
        logInWithStoreId("s-1");
        ClarityAdvice advice = new ClarityAdvice(null);

        // when / then
        assertNull(advice.clarityProjectId());
        assertNull(advice.clarityStoreId());
    }

    @Test
    void trackingEnabledWhenProjectIdConfigured() {
        // given
        logInWithStoreId("s-1");
        ClarityAdvice advice = new ClarityAdvice("xx4lvjxep2");

        // when / then
        assertEquals("xx4lvjxep2", advice.clarityProjectId());
        assertEquals("s-1", advice.clarityStoreId());
    }

    @Test
    void storeIdAbsentForAnonymousVisitor() {
        // given
        ClarityAdvice advice = new ClarityAdvice("xx4lvjxep2");

        // when / then
        assertEquals("xx4lvjxep2", advice.clarityProjectId());
        assertNull(advice.clarityStoreId());
    }

    private void logInWithStoreId(String storeId) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
    }
}
