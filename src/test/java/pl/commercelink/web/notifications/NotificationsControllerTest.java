package pl.commercelink.web.notifications;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.notifications.NotificationFilter;
import pl.commercelink.notifications.NotificationPage;
import pl.commercelink.notifications.StoreNotificationIds;
import pl.commercelink.notifications.StoreNotificationRecord;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoreNotificationService notificationService;
    @Mock
    private MessageSource messageSource;

    private MockedStatic<CustomSecurityContext> securityStub;
    private NotificationsController controller;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        controller = new NotificationsController(notificationService, new NotificationViewFactory(), messageSource);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    private static StoreNotificationRecord record(String storeId, StoreNotificationType type, String object) {
        StoreNotificationRecord record = new StoreNotificationRecord();
        record.setStoreId(storeId);
        record.setNotificationId(StoreNotificationIds.of(type, object, "Message"));
        record.setType(type);
        record.setSeverity(StoreNotificationSeverity.WARNING);
        record.setObject(object);
        record.setMessage("Message");
        record.setCreatedAt(LocalDateTime.of(2026, 9, 14, 15, 32));
        record.setUnreadStoreId(storeId);
        return record;
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersTheUnreadNotificationsOfTheAdminsStoreByDefault() {
        // given
        NotificationPage page = new NotificationPage(
                List.of(record("store-1", StoreNotificationType.UNAUTHENTICATED, "allegro_marketplace")), 1, 1, 1, 1);
        when(notificationService.list("store-1", new NotificationFilter(true, null), 1, 50)).thenReturn(page);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.notifications("unread", null, 1, model);

        // then
        assertThat(view).isEqualTo("notifications");
        assertThat(model.getAttribute("basePath")).isEqualTo("/dashboard/notifications");
        assertThat(model.getAttribute("notificationPage")).isSameAs(page);
        assertThat(model.getAttribute("unreadOnly")).isEqualTo(true);
        assertThat(model.getAttribute("selectedType")).isNull();
        assertThat((List<NotificationView>) model.getAttribute("notifications")).extracting(NotificationView::openHref)
                .containsExactly("/dashboard/notifications/UNAUTHENTICATED:allegro_marketplace/open");
        assertThat(model.getAttribute("types")).isEqualTo(StoreNotificationType.values());
        assertThat(model.getAttribute("unreadTabHref")).isEqualTo("/dashboard/notifications?filter=unread");
        assertThat(model.getAttribute("allTabHref")).isEqualTo("/dashboard/notifications?filter=all");
        assertThat(model.getAttribute("pageHref")).isEqualTo("/dashboard/notifications?filter=unread");
        assertThat(model.getAttribute("currentPage")).isEqualTo(1);
        assertThat(model.getAttribute("hasNextPage")).isEqualTo(false);
        assertThat(model.getAttribute("paginationParams")).isEqualTo(Map.of("filter", "unread"));
    }

    @Test
    void keepsTheFilterTypeAndPageInEveryLinkOfTheSuperAdminsStoreView() {
        // given
        NotificationPage page = new NotificationPage(List.of(), 2, 3, 120, 0);
        when(notificationService.list("store-9",
                new NotificationFilter(false, StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED), 2, 50)).thenReturn(page);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.storeNotifications("store-9", "all", "MARKETPLACE_RETURN_UNMATCHED", 2, model);

        // then
        assertThat(view).isEqualTo("notifications");
        assertThat(model.getAttribute("basePath")).isEqualTo("/dashboard/store/store-9/notifications");
        assertThat(model.getAttribute("selectedType")).isEqualTo("MARKETPLACE_RETURN_UNMATCHED");
        assertThat(model.getAttribute("unreadTabHref"))
                .isEqualTo("/dashboard/store/store-9/notifications?filter=unread&type=MARKETPLACE_RETURN_UNMATCHED");
        assertThat(model.getAttribute("pageHref"))
                .isEqualTo("/dashboard/store/store-9/notifications?filter=all&type=MARKETPLACE_RETURN_UNMATCHED&page=2");
        assertThat(model.getAttribute("hasNextPage")).isEqualTo(true);
        assertThat(model.getAttribute("paginationParams"))
                .isEqualTo(Map.of("filter", "all", "type", "MARKETPLACE_RETURN_UNMATCHED"));
    }

    @Test
    void ignoresAnUnknownTypeInsteadOfFailing() {
        // given
        when(notificationService.list("store-1", new NotificationFilter(true, null), 1, 50))
                .thenReturn(new NotificationPage(List.of(), 1, 1, 0, 0));
        Model model = new ExtendedModelMap();

        // when
        controller.notifications("unread", "NO_SUCH_TYPE", 1, model);

        // then
        assertThat(model.getAttribute("selectedType")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersTheTenNewestNotificationsForTheDropdown() {
        // given
        when(notificationService.latest("store-1", 10))
                .thenReturn(List.of(record("store-1", StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED, "ret-1")));
        when(notificationService.unreadCount("store-1")).thenReturn(4L);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.dropdown(model);

        // then
        assertThat(view).isEqualTo("fragments/notifications :: dropdown");
        assertThat(model.getAttribute("basePath")).isEqualTo("/dashboard/notifications");
        assertThat(model.getAttribute("unreadCount")).isEqualTo(4L);
        assertThat((List<NotificationView>) model.getAttribute("notifications")).hasSize(1);
    }

    @Test
    void scopesTheSuperAdminsDropdownToTheStoreInThePath() {
        // given
        when(notificationService.latest("store-9", 10)).thenReturn(List.of());
        when(notificationService.unreadCount("store-9")).thenReturn(0L);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.storeDropdown("store-9", model);

        // then
        assertThat(view).isEqualTo("fragments/notifications :: dropdown");
        assertThat(model.getAttribute("basePath")).isEqualTo("/dashboard/store/store-9/notifications");
    }

    @Test
    void openingANotificationMarksItReadAndFollowsItsAction() {
        // given
        when(notificationService.find("store-1", "UNAUTHENTICATED:allegro_marketplace"))
                .thenReturn(Optional.of(record("store-1", StoreNotificationType.UNAUTHENTICATED, "allegro_marketplace")));

        // when
        String redirect = controller.open("UNAUTHENTICATED:allegro_marketplace");

        // then
        verify(notificationService).markRead("store-1", List.of("UNAUTHENTICATED:allegro_marketplace"));
        assertThat(redirect).isEqualTo("redirect:/dashboard/store/marketplaces");
    }

    @Test
    void openingANotificationWithoutAnActionLeadsToAllNotifications() {
        // given
        when(notificationService.find("store-9", "MARKETPLACE_RETURN_REFUNDED:rma-7"))
                .thenReturn(Optional.of(record("store-9", StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, "rma-7")));

        // when
        String redirect = controller.storeOpen("store-9", "MARKETPLACE_RETURN_REFUNDED:rma-7");

        // then
        verify(notificationService).markRead("store-9", List.of("MARKETPLACE_RETURN_REFUNDED:rma-7"));
        assertThat(redirect).isEqualTo("redirect:/dashboard/store/store-9/notifications?filter=all");
    }

    @Test
    void openingAnUnknownNotificationLeadsToAllNotificationsWithoutMarkingAnything() {
        // given
        when(notificationService.find("store-1", "GONE:1")).thenReturn(Optional.empty());

        // when
        String redirect = controller.open("GONE:1");

        // then
        assertThat(redirect).isEqualTo("redirect:/dashboard/notifications?filter=all");
        verify(notificationService, never()).markRead(any(), any());
    }

    @Test
    void marksTheSelectedNotificationsReadAndReturnsToThePageTheOperatorWasOn() {
        // given
        when(notificationService.markRead("store-1", List.of("A:1", "A:2"))).thenReturn(2);
        when(messageSource.getMessage(eq("notifications.flash.markedRead"), eq(new Object[]{2}), eq(POLISH)))
                .thenReturn("Oznaczono jako przeczytane: 2");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        // when
        String redirect = controller.markRead(List.of("A:1", "A:2"), "/dashboard/notifications?filter=all&page=2",
                POLISH, attributes);

        // then
        assertThat(redirect).isEqualTo("redirect:/dashboard/notifications?filter=all&page=2");
        assertThat(attributes.getFlashAttributes().get("successMessage")).isEqualTo("Oznaczono jako przeczytane: 2");
    }

    @Test
    void marksTheSelectedNotificationsUnreadInTheSuperAdminsStore() {
        // given
        when(notificationService.markUnread("store-9", List.of("A:1"))).thenReturn(1);
        when(messageSource.getMessage(eq("notifications.flash.markedUnread"), eq(new Object[]{1}), eq(POLISH)))
                .thenReturn("Oznaczono jako nieprzeczytane: 1");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        // when
        String redirect = controller.storeMarkUnread("store-9", List.of("A:1"), null, POLISH, attributes);

        // then
        assertThat(redirect).isEqualTo("redirect:/dashboard/store/store-9/notifications");
        assertThat(attributes.getFlashAttributes().get("successMessage")).isEqualTo("Oznaczono jako nieprzeczytane: 1");
    }

    @Test
    void warnsInsteadOfMarkingWhenNothingIsSelected() {
        // given
        when(messageSource.getMessage(eq("notifications.flash.nothingSelected"), isNull(), eq(POLISH)))
                .thenReturn("Nie zaznaczono");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        // when
        String redirect = controller.markRead(null, "/dashboard/notifications", POLISH, attributes);

        // then
        assertThat(redirect).isEqualTo("redirect:/dashboard/notifications");
        assertThat(attributes.getFlashAttributes().get("warningMessage")).isEqualTo("Nie zaznaczono");
        verify(notificationService, never()).markRead(any(), any());
    }

    @Test
    void marksEverythingReadFromTheBellAndStaysOnTheCurrentPage() {
        // given
        when(notificationService.markAllRead("store-1")).thenReturn(3);
        when(messageSource.getMessage(eq("notifications.flash.markedRead"), eq(new Object[]{3}), eq(POLISH)))
                .thenReturn("Oznaczono jako przeczytane: 3");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        // when
        String redirect = controller.markAllRead("/dashboard/orders?status=New", POLISH, attributes);

        // then
        assertThat(redirect).isEqualTo("redirect:/dashboard/orders?status=New");
        assertThat(attributes.getFlashAttributes().get("successMessage")).isEqualTo("Oznaczono jako przeczytane: 3");
    }

    @Test
    void acceptsOnlyARedirectWithinTheDashboard() {
        for (String redirect : Arrays.asList(null, "", "https://evil.example/dashboard", "//evil.example/dashboard",
                "/dashboard//evil.example", "/dashboard\\evil", "/login")) {
            // when / then
            assertThat(NotificationsController.safeRedirect(redirect, "/dashboard/notifications"))
                    .as(String.valueOf(redirect))
                    .isEqualTo("/dashboard/notifications");
        }

        // when / then
        assertThat(NotificationsController.safeRedirect("/dashboard/store/marketplaces?lang=pl", "/dashboard/notifications"))
                .isEqualTo("/dashboard/store/marketplaces?lang=pl");
    }
}
