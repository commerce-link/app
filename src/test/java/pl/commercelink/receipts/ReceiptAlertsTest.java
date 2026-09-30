package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationType;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptAlertsTest {

    private final StoreNotificationService notifications = mock(StoreNotificationService.class);
    private final StaticMessageSource messages = new StaticMessageSource();
    private final ReceiptProviderFactory providerFactory = mock(ReceiptProviderFactory.class);
    private final ReceiptAlerts alerts = new ReceiptAlerts(notifications, messages, providerFactory);

    private ReceiptAttempt attempt(String attention) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("s1");
        attempt.setReceiptKey("o1:R1");
        attempt.setOrderId("o1");
        attempt.setAttention(attention);
        return attempt;
    }

    @Test
    void aNewReasonReplacesTheNotification() {
        messages.addMessage("receipts.attention.FAILED", new Locale("pl"), "Paragon {1} odrzucony: {3}");
        ReceiptAttempt attempt = attempt(ReceiptAttention.PENDING_LONG.name());
        attempt.setFailureMessage("VAT");

        assertThat(alerts.sync(attempt, ReceiptAttention.FAILED)).isTrue();

        verify(notifications).resolve("s1", StoreNotificationType.RECEIPT_ATTENTION, "o1:R1");
        verify(notifications).publish(eq("s1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.RECEIPT_ATTENTION && "o1:R1".equals(n.getObject())
                        && n.getMessage().equals("Paragon o1:R1 odrzucony: VAT")));
        assertThat(attempt.getAttention()).isEqualTo("FAILED");
    }

    @Test
    void anUnchangedReasonDoesNothing() {
        assertThat(alerts.sync(attempt("FAILED"), ReceiptAttention.FAILED)).isFalse();
        verifyNoInteractions(notifications);
    }

    @Test
    void republishPublishesEvenWhenTheStoredAttentionIsUnchanged() {
        // given: the alert was resolved by a closing document that has been unpinned since
        messages.addMessage("receipts.attention.BLOCKED", new Locale("pl"), "Paragon {1} wstrzymany");
        ReceiptAttempt attempt = attempt(ReceiptAttention.BLOCKED.name());

        // when
        boolean changed = alerts.republish(attempt, ReceiptAttention.BLOCKED);

        // then: published into the existing record, if any (read or not), never resolved first
        assertThat(changed).isFalse();
        verify(notifications).publish(eq("s1"), argThat((StoreNotification n) ->
                "o1:R1".equals(n.getObject()) && n.getMessage().equals("Paragon o1:R1 wstrzymany")));
        verify(notifications, never()).resolve(any(), any(), any());
    }

    @Test
    void republishReplacesTheNotificationWhenTheStoredAttentionDiffers() {
        // given: the processor kept the bell silent for a settled order
        messages.addMessage("receipts.attention.BLOCKED", new Locale("pl"), "Paragon {1} wstrzymany");
        ReceiptAttempt attempt = attempt(null);

        // when
        boolean changed = alerts.republish(attempt, ReceiptAttention.BLOCKED);

        // then
        assertThat(changed).isTrue();
        verify(notifications).resolve("s1", StoreNotificationType.RECEIPT_ATTENTION, "o1:R1");
        verify(notifications).publish(eq("s1"), any());
        assertThat(attempt.getAttention()).isEqualTo("BLOCKED");
    }

    @Test
    void aSolvedProblemRemovesTheNotification() {
        ReceiptAttempt attempt = attempt("PENDING_LONG");

        assertThat(alerts.sync(attempt, null)).isTrue();

        verify(notifications).resolve("s1", StoreNotificationType.RECEIPT_ATTENTION, "o1:R1");
        verify(notifications, never()).publish(any(), any());
        assertThat(attempt.getAttention()).isNull();
    }

    @Test
    void theOutcomeOfADeadAttemptIsItsBlockedReasonOrTheProvidersRefusalWithoutAdvice() {
        // given
        messages.addMessage("receipts.blocked.NO_LINES", new Locale("pl"), "brak pozycji");
        ReceiptAttempt blocked = attempt(null);
        blocked.setState(ReceiptAttemptState.BLOCKED);
        blocked.setBlockedReason("NO_LINES");
        blocked.setBlockedDetail("0 zł");
        ReceiptAttempt failed = attempt(null);
        failed.setState(ReceiptAttemptState.FAILED);
        failed.setFailureMessage("Nieprawidłowa stawka VAT");
        ReceiptAttempt fiscalised = attempt(null);
        fiscalised.setState(ReceiptAttemptState.FISCALISED);
        fiscalised.setFailureMessage("stale");

        // when / then
        assertThat(alerts.outcome(blocked, new Locale("pl"))).isEqualTo("brak pozycji (0 zł)");
        assertThat(alerts.outcome(failed, new Locale("pl"))).isEqualTo("Nieprawidłowa stawka VAT");
        assertThat(alerts.outcome(fiscalised, new Locale("pl"))).isNull();
    }

    // ---- the order page's wording (pageProblem), against the real message bundles ----

    private static final String ORDER_ID = "ORDER-77";
    private static final String KEY = ORDER_ID + ":R3";

    private static ResourceBundleMessageSource bundles() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    private ReceiptAlerts pageAlerts() {
        ReceiptProviderDescriptor fakturownia = mock(ReceiptProviderDescriptor.class);
        when(fakturownia.displayName()).thenReturn("Paragony.pl (Fakturownia)");
        ReceiptProviderDescriptor dev = mock(ReceiptProviderDescriptor.class);
        when(dev.displayName()).thenReturn("Dev Receipts");
        when(providerFactory.getDescriptor("fakturownia")).thenReturn(fakturownia);
        when(providerFactory.getDescriptor("receipts-dev")).thenReturn(dev);
        return new ReceiptAlerts(notifications, bundles(), providerFactory);
    }

    /** An attempt carrying every value a page text may use, so a text that leaks an id would show it. */
    private static ReceiptAttempt troubled(String provider) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("s1");
        attempt.setOrderId(ORDER_ID);
        attempt.setReceiptKey(KEY);
        attempt.setProvider(provider);
        attempt.setLastError("read timed out");
        attempt.setFailureMessage("Nieprawidłowa stawka VAT.");
        attempt.setBlockedReason("MISSING_EMAIL");
        attempt.setIssueCalls(7);
        return attempt;
    }

    @Test
    void everyAttentionHasAPageCauseAndActionWithoutTheReceiptKeyOrOrderIdInBothLanguages() {
        // given
        ReceiptAlerts page = pageAlerts();

        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            for (ReceiptAttention attention : ReceiptAttention.values()) {
                // when
                ReceiptPageProblem problem = page.pageProblem(troubled("fakturownia"), attention, locale);

                // then
                String what = locale + " " + attention;
                assertThat(problem.cause()).as(what).isNotBlank().doesNotContain(ORDER_ID).doesNotContain("{")
                        .isNotEqualTo(attention.name());
                assertThat(problem.action()).as(what).isNotBlank().doesNotContain(ORDER_ID).doesNotContain("{");
                assertThat(problem.cause() + problem.action()).as(what)
                        .doesNotContain("fiscal_status").doesNotContain("commercelink:fiscal-print-ordered")
                        .doesNotContain("..");
                if (problem.details() != null) {
                    assertThat(problem.detailsSummary()).as(what).contains("Paragony.pl (Fakturownia)");
                    assertThat(problem.details()).as(what).doesNotContain("{");
                } else {
                    assertThat(problem.detailsSummary()).as(what).isNull();
                }
            }
        }
    }

    @Test
    void thePageCausesNameTheAttemptsProviderByItsDisplayName() {
        // given
        ReceiptAlerts page = pageAlerts();
        Locale pl = Locale.forLanguageTag("pl");

        // when / then
        assertThat(page.pageProblem(troubled("fakturownia"), ReceiptAttention.FAILED, pl).cause())
                .isEqualTo("System Paragony.pl (Fakturownia) odrzucił paragon: Nieprawidłowa stawka VAT.");
        assertThat(page.pageProblem(troubled("fakturownia"), ReceiptAttention.FAILED, pl).action())
                .isEqualTo("Popraw przyczynę i kliknij „Wystaw ponownie”. "
                        + "Nie zlecaj starego paragonu w systemie Paragony.pl (Fakturownia).");
        for (ReceiptAttention attention : List.of(ReceiptAttention.FAILED, ReceiptAttention.INVALID_AFTER_SEND,
                ReceiptAttention.ISSUING_UNKNOWN, ReceiptAttention.PROVIDER_UNAVAILABLE)) {
            assertThat(page.pageProblem(troubled("receipts-dev"), attention, pl).cause()).as(attention.name())
                    .contains("Dev Receipts");
            assertThat(page.pageProblem(troubled("receipts-dev"), attention, Locale.ENGLISH).cause())
                    .as(attention.name()).contains("Dev Receipts");
        }
    }

    @Test
    void theProviderIdStandsInForItsNameOnceItsAdapterIsGone() {
        // given: no descriptor for the stored provider id
        ReceiptAlerts page = pageAlerts();

        // when
        ReceiptPageProblem problem = page.pageProblem(troubled("old-system"), ReceiptAttention.FAILED,
                Locale.forLanguageTag("pl"));

        // then
        assertThat(problem.cause()).isEqualTo("System old-system odrzucił paragon: Nieprawidłowa stawka VAT.");
    }

    @Test
    void anAttemptWithoutAProviderIsTheEReceiptSystem() {
        // given
        ReceiptAlerts page = pageAlerts();

        // when
        ReceiptPageProblem problem = page.pageProblem(troubled(null), ReceiptAttention.FAILED, Locale.forLanguageTag("pl"));

        // then
        assertThat(problem.cause()).isEqualTo("System e-paragonów odrzucił paragon: Nieprawidłowa stawka VAT.");
    }

    @Test
    void theFiscalPrinterHintsAreFakturowniasOwnAndFoldedUnderItsDetails() {
        // given
        ReceiptAlerts page = pageAlerts();
        Locale pl = Locale.forLanguageTag("pl");

        // when
        ReceiptPageProblem fakturownia = page.pageProblem(troubled("fakturownia"), ReceiptAttention.PENDING_LONG, pl);
        ReceiptPageProblem dev = page.pageProblem(troubled("receipts-dev"), ReceiptAttention.PENDING_LONG, pl);

        // then: the agreed wording for Fakturownia, and none of its internals for another provider
        assertThat(fakturownia.cause()).isEqualTo("Paragon czeka na drukarkę fiskalną ponad 48 h.");
        assertThat(fakturownia.action()).isEqualTo("Sprawdź, czy drukarka albo moduł Paragony.pl działa.");
        assertThat(fakturownia.detailsSummary()).isEqualTo("Szczegóły dla Paragony.pl (Fakturownia)");
        assertThat(fakturownia.details()).contains("to_print").contains("commercelink:fiscal-print-ordered")
                .contains("Nigdy nie zlecaj paragonu, który ma jakikolwiek fiscal_status.");
        assertThat(dev.action()).isEqualTo("Sprawdź, czy drukarka fiskalna działa.");
        assertThat(dev.details()).isNull();
        assertThat(dev.detailsSummary()).isNull();
    }

    @Test
    void theUnknownOutcomeKeepsTheSafetyAdviceAndNamesTheKeyOnlyInTheDetails() {
        // given
        ReceiptAlerts page = pageAlerts();
        Locale pl = Locale.forLanguageTag("pl");

        // when
        ReceiptPageProblem unknown = page.pageProblem(troubled("fakturownia"), ReceiptAttention.ISSUING_UNKNOWN, pl);
        ReceiptPageProblem invalid = page.pageProblem(troubled("fakturownia"), ReceiptAttention.INVALID_AFTER_SEND, pl);

        // then
        assertThat(unknown.cause()).contains("po 7 próbach").contains("read timed out").doesNotContain(KEY);
        assertThat(unknown.action()).contains("nie wystawiaj nowego, dopóki nie wiesz, że poprzedni nie został "
                + "zafiskalizowany");
        assertThat(unknown.details()).contains(KEY).contains("nic nie ruszaj i zgłoś to");
        assertThat(invalid.action()).contains("dopóki nie wiesz").contains("„Zamknij ręcznie”");
    }

    @Test
    void blockedAndEmailProblemsPointAtTheRowsButtons() {
        // given
        ReceiptAlerts page = pageAlerts();
        Locale pl = Locale.forLanguageTag("pl");
        ReceiptAttempt blocked = troubled("fakturownia");
        blocked.setBlockedDetail("pusty adres");

        // when / then
        ReceiptPageProblem blockedProblem = page.pageProblem(blocked, ReceiptAttention.BLOCKED, pl);
        assertThat(blockedProblem.cause()).isEqualTo("Paragonu nie wysłano: brak e-maila kupującego (pusty adres).");
        assertThat(blockedProblem.action()).isEqualTo("Popraw dane zamówienia i kliknij „Wystaw ponownie”.");
        ReceiptPageProblem email = page.pageProblem(troubled("fakturownia"), ReceiptAttention.EMAIL_NOT_SENT, pl);
        assertThat(email.cause()).isEqualTo("Mail z e-paragonem nie wyszedł.");
        assertThat(email.action()).isEqualTo("Sprawdź szablon „E-paragon” i kliknij „Wyślij mail ponownie”.");
        assertThat(page.pageProblem(troubled("fakturownia"), ReceiptAttention.EFFECTS_FAILED, pl).cause())
                .contains("marketplace'u");
    }

    @Test
    void aMissingErrorReadsAsNoDetailsRatherThanAnEmptyGap() {
        // given
        ReceiptAlerts page = pageAlerts();
        ReceiptAttempt attempt = troubled("fakturownia");
        attempt.setFailureMessage(null);

        // when
        String cause = page.pageProblem(attempt, ReceiptAttention.FAILED, Locale.forLanguageTag("pl")).cause();

        // then
        assertThat(cause).isEqualTo("System Paragony.pl (Fakturownia) odrzucił paragon: brak szczegółów.");
    }

    @Test
    void theBellMessageIsUnchangedByThePageWording() {
        // given
        ReceiptAlerts page = pageAlerts();

        // when
        String bell = page.message(troubled("fakturownia"), ReceiptAttention.FAILED);

        // then: the bell still names the receipt, as before
        assertThat(bell).startsWith("Paragon " + KEY + " nie został zafiskalizowany: Nieprawidłowa stawka VAT.");
    }

    @Test
    void aPosSaleWithoutTheCustomersEmailGetsTheCashRegisterInstructions() {
        // given
        messages.addMessage("receipts.attention.BLOCKED_POS", new Locale("pl"), "POS {0}: wpisz numer z kasy albo e-mail");
        messages.addMessage("receipts.attention.BLOCKED", new Locale("pl"), "Zablokowany {0}");
        ReceiptAttempt attempt = attempt(null);
        attempt.setBlockedReason(ReceiptBlockReason.POS_NO_CUSTOMER_EMAIL.name());

        // when
        String message = alerts.message(attempt, ReceiptAttention.BLOCKED);

        // then
        assertThat(message).isEqualTo("POS o1: wpisz numer z kasy albo e-mail");
    }

    @Test
    void otherBlockedReasonsKeepTheGeneralMessage() {
        // given
        messages.addMessage("receipts.attention.BLOCKED_POS", new Locale("pl"), "POS {0}");
        messages.addMessage("receipts.attention.BLOCKED", new Locale("pl"), "Zablokowany {0}");
        ReceiptAttempt attempt = attempt(null);
        attempt.setBlockedReason(ReceiptBlockReason.MISSING_EMAIL.name());

        // when
        String message = alerts.message(attempt, ReceiptAttention.BLOCKED);

        // then
        assertThat(message).isEqualTo("Zablokowany o1");
    }

    @Test
    void aPosSaleWithoutTheCustomersEmailGetsTheCashRegisterAdviceOnThePageToo() {
        // given
        ReceiptAlerts page = pageAlerts();
        ReceiptAttempt pos = troubled("fakturownia");
        pos.setBlockedReason(ReceiptBlockReason.POS_NO_CUSTOMER_EMAIL.name());
        Locale pl = Locale.forLanguageTag("pl");

        // when
        ReceiptPageProblem problem = page.pageProblem(pos, ReceiptAttention.BLOCKED, pl);
        ReceiptPageProblem english = page.pageProblem(pos, ReceiptAttention.BLOCKED, Locale.ENGLISH);

        // then: the buttons of the new page, one alternative per line with its condition first, and never both
        assertThat(problem.cause()).isEqualTo("E-paragonu nie wysłano: sprzedaż POS nie ma e-maila klienta, "
                + "a paragon mogła już wydrukować kasa sklepu.");
        assertThat(problem.actions()).containsExactly(
                "Kasa wydrukowała paragon? Wpisz jego numer: „Dodaj dokument”\u00a0→\u00a0Paragon.",
                "Klient chce e-paragon? Wpisz jego e-mail w danych rozliczeniowych, potem „Wystaw ponownie”.",
                "Nie rób obu — sprzedaż zostałaby zafiskalizowana dwa razy.");
        assertThat(problem.action()).isEqualTo(String.join(" ", problem.actions())).doesNotContain(ORDER_ID);
        assertThat(english.cause()).contains("shop's cash register");
        assertThat(english.actions()).hasSize(3);
        assertThat(english.actions().get(0)).contains("\"Add document\"\u00a0→\u00a0Receipt");
        assertThat(english.actions().get(2)).isEqualTo("Never both: the sale would be fiscalised twice.");
    }
}
