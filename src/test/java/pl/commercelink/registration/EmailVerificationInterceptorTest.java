package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationInterceptorTest {

    @Mock private EmailVerificationService emailVerificationService;
    @InjectMocks private EmailVerificationInterceptor interceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void letsVerifiedUserThrough() throws Exception {
        // given
        when(emailVerificationService.isVerified(request)).thenReturn(true);

        // when
        boolean proceed = interceptor.preHandle(request, response, new Object());

        // then
        assertTrue(proceed);
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void redirectsUnverifiedUserToVerificationScreen() throws Exception {
        // given
        when(emailVerificationService.isVerified(request)).thenReturn(false);

        // when
        boolean proceed = interceptor.preHandle(request, response, new Object());

        // then
        assertFalse(proceed);
        assertEquals("/register/verify-email", response.getRedirectedUrl());
    }
}
