package pl.commercelink.web.notifications;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationBellAdviceTest {

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Mock
    private StoreNotificationService notificationService;

    @InjectMocks
    private NotificationBellAdvice advice;

    private void loggedInAs(Map<String, String> customAttributes) {
        CustomUser user = new CustomUser(
                new DefaultOAuth2User(List.of(), Map.of("sub", "user-1"), "sub"), null, customAttributes);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static MockHttpServletRequest pageView(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader(HttpHeaders.ACCEPT, BROWSER_ACCEPT);
        return request;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void showsTheAdminTheUnreadCountOfTheirOwnStore() {
        // given
        loggedInAs(Map.of("role", "ADMIN", "storeId", "store-1"));
        when(notificationService.unreadCount("store-1")).thenReturn(3L);

        // when
        NotificationBell bell = advice.notificationBell(pageView("/dashboard/orders"));

        // then
        assertThat(bell).isEqualTo(new NotificationBell(3, "/dashboard/notifications/dropdown", "/dashboard/notifications"));
    }

    @Test
    void showsTheSuperAdminTheBellOfTheStoreTheyAreWorkingIn() {
        // given
        loggedInAs(Map.of("role", "SUPER_ADMIN"));
        when(notificationService.unreadCount("store-9")).thenReturn(0L);

        // when
        NotificationBell bell = advice.notificationBell(pageView("/dashboard/store/store-9/deliveries"));

        // then
        assertThat(bell).isEqualTo(new NotificationBell(0, "/dashboard/store/store-9/notifications/dropdown",
                "/dashboard/store/store-9/notifications"));
    }

    @Test
    void hidesTheBellFromTheSuperAdminOutsideAStore() {
        // given
        loggedInAs(Map.of("role", "SUPER_ADMIN"));

        // when
        NotificationBell storesList = advice.notificationBell(pageView("/dashboard/stores"));
        NotificationBell reservedSegment = advice.notificationBell(pageView("/dashboard/store/rma-centers"));

        // then
        assertThat(storesList).isNull();
        assertThat(reservedSegment).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void hidesTheBellFromAStoreUser() {
        // given
        loggedInAs(Map.of("role", "USER", "storeId", "store-1"));

        // when / then
        assertThat(advice.notificationBell(pageView("/dashboard/orders"))).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void hidesTheBellWithoutALoggedInUser() {
        // when / then
        assertThat(advice.notificationBell(pageView("/dashboard/orders"))).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void hidesTheBellFromAnAdminWithoutAStore() {
        // given
        loggedInAs(Map.of("role", "ADMIN"));

        // when / then
        assertThat(advice.notificationBell(pageView("/dashboard/orders"))).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void skipsTheCountForRequestsThatDoNotRenderAPage() {
        // given
        loggedInAs(Map.of("role", "ADMIN", "storeId", "store-1"));
        MockHttpServletRequest fetch = new MockHttpServletRequest("GET", "/dashboard/notifications/dropdown");
        fetch.addHeader(HttpHeaders.ACCEPT, "*/*");
        MockHttpServletRequest withoutAccept = new MockHttpServletRequest("POST", "/dashboard/store/integrations/device-auth/poll");

        // when / then
        assertThat(advice.notificationBell(fetch)).isNull();
        assertThat(advice.notificationBell(withoutAccept)).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void hidesTheBellForAPostEvenWithAnHtmlAccept() {
        // given
        loggedInAs(Map.of("role", "ADMIN", "storeId", "store-1"));
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/dashboard/notifications/mark-all-read");
        post.addHeader(HttpHeaders.ACCEPT, BROWSER_ACCEPT);

        // when / then
        assertThat(advice.notificationBell(post)).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void rendersThePageWithoutTheBellWhenTheCountFails() {
        // given
        loggedInAs(Map.of("role", "ADMIN", "storeId", "store-1"));
        when(notificationService.unreadCount("store-1")).thenThrow(new IllegalStateException("DynamoDB unavailable"));

        // when / then
        assertThat(advice.notificationBell(pageView("/dashboard/orders"))).isNull();
    }
}
