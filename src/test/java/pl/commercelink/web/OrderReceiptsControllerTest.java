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
import pl.commercelink.web.settings.ConfirmAction;

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

    @Test
    void reissueRedirectsWithSuccess() {
        when(attemptService.reissue(STORE_ID, ORDER_ID, "Jan Kowalski")).thenReturn(new ReceiptAttempt());
        when(messageSource.getMessage("receipts.action.reissue.done", null, LOCALE)).thenReturn("Nowy paragon jest wystawiany.");

        String view = controller.reissue(ORDER_ID, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("successMessage")).isEqualTo("Nowy paragon jest wystawiany.");
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
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("successMessage");
    }

    @Test
    void checkPassesTheKey() {
        String receiptKey = ORDER_ID + ":R1";
        when(messageSource.getMessage("receipts.action.check.done", null, LOCALE)).thenReturn("Stan paragonu zostanie sprawdzony za chwilę.");

        String view = controller.check(ORDER_ID, receiptKey, LOCALE, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("successMessage")).isEqualTo("Stan paragonu zostanie sprawdzony za chwilę.");
        verify(attemptService).checkNow(STORE_ID, receiptKey);
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
        assertThat(redirectAttributes.getFlashAttributes().get("successMessage")).isEqualTo("Paragon zamknięty ręcznie.");
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
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("successMessage");
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
        when(messageSource.getMessage(eq("receipts.action.reissue.confirm.back"), any(), eq(LOCALE))).thenReturn("Powrót do zamówienia");
        Model model = new ExtendedModelMap();

        String view = controller.confirmReissue(ORDER_ID, LOCALE, model);

        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm).isNotNull();
        assertThat(confirm.title()).isEqualTo("Wystawić nowy paragon?");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/orders/" + ORDER_ID + "/receipts/reissue");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/orders/" + ORDER_ID);
        assertThat(model.getAttribute("backLabel")).isEqualTo("Powrót do zamówienia");
    }
}
