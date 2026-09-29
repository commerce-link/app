package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.receipts.ReceiptActionException;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.orders.ReceiptCloseForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderReceiptsControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final Locale LOCALE = Locale.ENGLISH;

    @Mock
    private ReceiptAttemptService attemptService;
    @Mock
    private MessageSource messageSource;

    private OrderReceiptsController controller;
    private RedirectAttributes redirectAttributes;
    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        controller = new OrderReceiptsController(attemptService, messageSource);
        redirectAttributes = new RedirectAttributesModelMap();
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        CustomUser user = mock(CustomUser.class);
        when(user.getName()).thenReturn("Jan Kowalski");
        securityStub.when(CustomSecurityContext::getLoggedInUser).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    /** A success is the order page's own notice (OrderFlash), like every other action of the redesigned page. */
    private String notice() {
        OrderNotice notice = (OrderNotice) redirectAttributes.getFlashAttributes().get(OrderFlash.ATTRIBUTE);
        assertThat(notice).isNotNull();
        assertThat(notice.tone()).isEqualTo(OrderLabels.OK);
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("successMessage");
        return notice.text();
    }

    @Test
    void reissueRedirectsWithSuccess() {
        when(attemptService.reissue(STORE_ID, ORDER_ID, "Jan Kowalski")).thenReturn(new ReceiptAttempt());
        when(messageSource.getMessage("receipts.action.reissue.done", null, LOCALE)).thenReturn("Nowy paragon jest wystawiany.");

        String view = controller.reissue(ORDER_ID, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(notice()).isEqualTo("Nowy paragon jest wystawiany.");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("errorMessage");
        verify(attemptService).reissue(STORE_ID, ORDER_ID, "Jan Kowalski");
    }

    @Test
    void refusedReissueShowsTheReason() {
        when(attemptService.reissue(STORE_ID, ORDER_ID, "Jan Kowalski"))
                .thenThrow(new ReceiptActionException("receipts.action.reissue.live"));
        when(messageSource.getMessage("receipts.action.reissue.live", null, LOCALE))
                .thenReturn("Poprzedni paragon nie jest rozstrzygnięty — nowego nie można wystawić.");

        String view = controller.reissue(ORDER_ID, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Poprzedni paragon nie jest rozstrzygnięty — nowego nie można wystawić.");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(OrderFlash.ATTRIBUTE);
    }

    @Test
    void issueStartsTheFirstAttemptAsTheOperator() {
        when(attemptService.issueManually(STORE_ID, ORDER_ID, "Jan Kowalski")).thenReturn(new ReceiptAttempt());
        when(messageSource.getMessage("receipts.action.issue.done", null, LOCALE)).thenReturn("E-paragon jest wystawiany.");

        String view = controller.issue(ORDER_ID, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(notice()).isEqualTo("E-paragon jest wystawiany.");
        verify(attemptService).issueManually(STORE_ID, ORDER_ID, "Jan Kowalski");
    }

    @Test
    void refusedIssueShowsTheReason() {
        when(attemptService.issueManually(STORE_ID, ORDER_ID, "Jan Kowalski"))
                .thenThrow(new ReceiptActionException("receipts.action.issue.exists"));
        when(messageSource.getMessage("receipts.action.issue.exists", null, LOCALE)).thenReturn("Ma już e-paragon.");

        controller.issue(ORDER_ID, LOCALE, redirectAttributes);

        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Ma już e-paragon.");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(OrderFlash.ATTRIBUTE);
    }

    @Test
    void checkPassesTheKey() {
        String receiptKey = ORDER_ID + ":R1";
        when(messageSource.getMessage("receipts.action.check.done", null, LOCALE)).thenReturn("Stan paragonu zostanie sprawdzony za chwilę.");

        String view = controller.check(ORDER_ID, receiptKey, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(notice()).isEqualTo("Stan paragonu zostanie sprawdzony za chwilę.");
        verify(attemptService).checkNow(STORE_ID, receiptKey);
    }

    @Test
    void resendEmailPassesTheKey() {
        String receiptKey = ORDER_ID + ":R1";
        when(messageSource.getMessage("receipts.flash.emailResent", null, LOCALE))
                .thenReturn("The e-receipt e-mail will be sent again.");

        String view = controller.resendEmail(ORDER_ID, receiptKey, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(notice()).isEqualTo("The e-receipt e-mail will be sent again.");
        verify(attemptService).resendEmail(STORE_ID, ORDER_ID, receiptKey, "Jan Kowalski");
    }

    @Test
    void resendEmailWithAnotherOrdersKeyIsRefused() {
        String otherOrdersKey = "other-order:R1";
        when(messageSource.getMessage("receipts.action.notFound", null, LOCALE)).thenReturn("Nie znaleziono paragonu.");

        String view = controller.resendEmail(ORDER_ID, otherOrdersKey, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Nie znaleziono paragonu.");
        verifyNoInteractions(attemptService);
    }

    @Test
    void refusedResendEmailShowsTheReason() {
        String receiptKey = ORDER_ID + ":R1";
        org.mockito.Mockito.doThrow(new ReceiptActionException("receipts.action.resendEmail.notEligible"))
                .when(attemptService).resendEmail(STORE_ID, ORDER_ID, receiptKey, "Jan Kowalski");
        when(messageSource.getMessage("receipts.action.resendEmail.notEligible", null, LOCALE))
                .thenReturn("This e-mail is not waiting to be resent.");

        String view = controller.resendEmail(ORDER_ID, receiptKey, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("This e-mail is not waiting to be resent.");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(OrderFlash.ATTRIBUTE);
    }

    @Test
    void closeNeedsANumber() {
        String receiptKey = ORDER_ID + ":R1";
        when(messageSource.getMessage("receipts.action.close.numberRequired", null, LOCALE)).thenReturn("Podaj numer paragonu.");

        String view = controller.close(ORDER_ID, receiptKey, null, null, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Podaj numer paragonu.");
        verify(attemptService, never()).closeManually(any(), any(), any(), any(), any());
    }

    @Test
    void closePassesNumberAndLink() {
        String receiptKey = ORDER_ID + ":R1";
        when(messageSource.getMessage("receipts.action.close.done", null, LOCALE)).thenReturn("Paragon zamknięty ręcznie.");

        String view = controller.close(ORDER_ID, receiptKey, "PAR/1", "https://x", LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(notice()).isEqualTo("Paragon zamknięty ręcznie.");
        verify(attemptService).closeManually(STORE_ID, receiptKey, "PAR/1", "https://x", "Jan Kowalski");
    }

    @Test
    void closeWithAnInvalidLinkShowsTheReason() {
        String receiptKey = ORDER_ID + ":R1";
        org.mockito.Mockito.doThrow(new ReceiptActionException("receipts.action.close.invalidLink"))
                .when(attemptService).closeManually(STORE_ID, receiptKey, "PAR/1", "javascript:x", "Jan Kowalski");
        when(messageSource.getMessage("receipts.action.close.invalidLink", null, LOCALE))
                .thenReturn("Link do e-paragonu musi zaczynać się od http:// lub https://.");

        String view = controller.close(ORDER_ID, receiptKey, "PAR/1", "javascript:x", LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Link do e-paragonu musi zaczynać się od http:// lub https://.");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(OrderFlash.ATTRIBUTE);
    }

    @Test
    void keysOfAnotherOrderAreRefused() {
        String otherOrdersKey = "other-order:R1";
        when(messageSource.getMessage("receipts.action.notFound", null, LOCALE)).thenReturn("Nie znaleziono paragonu.");

        String view = controller.check(ORDER_ID, otherOrdersKey, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Nie znaleziono paragonu.");
        verifyNoInteractions(attemptService);
    }

    @Test
    void confirmReissueRendersTheConfirmationPage() {
        when(messageSource.getMessage(eq("receipts.action.reissue.confirm.title"), any(), eq(LOCALE))).thenReturn("Wystawić nowy paragon?");
        when(messageSource.getMessage(eq("receipts.action.reissue.confirm.message"), any(), eq(LOCALE))).thenReturn("message");
        when(messageSource.getMessage(eq("receipts.action.reissue"), any(), eq(LOCALE))).thenReturn("Wystaw ponownie");
        when(messageSource.getMessage(eq("order.page.title"), any(), eq(LOCALE))).thenReturn("Zamówienie order-1");
        Model model = new ExtendedModelMap();

        String view = controller.confirmReissue(ORDER_ID, LOCALE, model);

        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm).isNotNull();
        assertThat(confirm.title()).isEqualTo("Wystawić nowy paragon?");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/orders/" + ORDER_ID + "/receipts/reissue");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/orders/" + ORDER_ID);
        // back to the order by its number, as the order page's own confirmation pages lead
        assertThat(model.getAttribute("backLabel")).isEqualTo("Zamówienie order-1");
    }

    private static ReceiptAttempt attempt(String receiptKey, int attemptNo, ReceiptAttemptState state) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey(receiptKey);
        attempt.setAttemptNo(attemptNo);
        attempt.setState(state);
        return attempt;
    }

    @Test
    void confirmIssueRendersAPrimaryConfirmationPageThatPostsTheIssue() {
        // given
        when(attemptService.attemptsOf(STORE_ID, ORDER_ID)).thenReturn(List.of());
        when(messageSource.getMessage(eq("receipts.action.issue.confirm.title"), any(), eq(LOCALE))).thenReturn("Wystawić e-paragon?");
        when(messageSource.getMessage(eq("receipts.action.issue.confirm.message"), any(), eq(LOCALE))).thenReturn("message");
        when(messageSource.getMessage(eq("receipts.action.issue.confirm.action"), any(), eq(LOCALE))).thenReturn("Wystaw e-paragon");
        when(messageSource.getMessage(eq("order.page.title"), any(), eq(LOCALE))).thenReturn("Zamówienie order-1");
        Model model = new ExtendedModelMap();

        // when
        String view = controller.confirmIssue(ORDER_ID, LOCALE, model, redirectAttributes);

        // then: issuing is not a removal, so the button is the primary one, not red
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm.title()).isEqualTo("Wystawić e-paragon?");
        assertThat(confirm.confirmLabel()).isEqualTo("Wystaw e-paragon");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/orders/" + ORDER_ID + "/receipts/issue");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/orders/" + ORDER_ID);
        assertThat(confirm.destructive()).isFalse();
        assertThat(model.getAttribute("backLabel")).isEqualTo("Zamówienie order-1");
        verify(attemptService, never()).issueManually(any(), any(), any());
    }

    @Test
    void confirmIssueOfAnOrderThatAlreadyHasAnAttemptGoesBackWithTheReason() {
        // given
        when(attemptService.attemptsOf(STORE_ID, ORDER_ID))
                .thenReturn(List.of(attempt(ORDER_ID + ":R1", 1, ReceiptAttemptState.FAILED)));
        when(messageSource.getMessage("receipts.action.issue.exists", null, LOCALE)).thenReturn("Ma już e-paragon.");

        // when
        String view = controller.confirmIssue(ORDER_ID, LOCALE, new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Ma już e-paragon.");
    }

    @Test
    void closePageRendersTheFormOfAHungAttempt() {
        // given
        String receiptKey = ORDER_ID + ":R2";
        when(attemptService.attemptsOf(STORE_ID, ORDER_ID)).thenReturn(List.of(
                attempt(ORDER_ID + ":R1", 1, ReceiptAttemptState.FAILED), attempt(receiptKey, 2, ReceiptAttemptState.PENDING)));
        Model model = new ExtendedModelMap();

        // when
        String view = controller.closePage(ORDER_ID, receiptKey, LOCALE, model, redirectAttributes);

        // then
        assertThat(view).isEqualTo("orders/receipt-close");
        assertThat(model.getAttribute("close")).isEqualTo(new ReceiptCloseForm(ORDER_ID, receiptKey, 2));
        assertThat(model.getAttribute("shortId")).isNotNull();
    }

    @Test
    void closePageOfAnAttemptThatCannotBeClosedGoesBackWithTheReason() {
        // given
        String receiptKey = ORDER_ID + ":R1";
        when(attemptService.attemptsOf(STORE_ID, ORDER_ID))
                .thenReturn(List.of(attempt(receiptKey, 1, ReceiptAttemptState.FISCALISED)));
        when(messageSource.getMessage("receipts.action.close.notHung", null, LOCALE)).thenReturn("Tylko w trakcie wystawiania.");
        Model model = new ExtendedModelMap();

        // when
        String view = controller.closePage(ORDER_ID, receiptKey, LOCALE, model, redirectAttributes);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Tylko w trakcie wystawiania.");
        assertThat(model.getAttribute("close")).isNull();
    }

    @Test
    void closePageWithAnotherOrdersKeyIsRefusedWithoutReadingAttempts() {
        // given
        when(messageSource.getMessage("receipts.action.notFound", null, LOCALE)).thenReturn("Nie znaleziono paragonu.");

        // when
        String view = controller.closePage(ORDER_ID, "other-order:R1", LOCALE, new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Nie znaleziono paragonu.");
        verifyNoInteractions(attemptService);
    }
}
