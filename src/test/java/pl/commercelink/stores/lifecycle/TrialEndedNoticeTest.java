package pl.commercelink.stores.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.TrialPeriod;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrialEndedNoticeTest {

    private static final DeactivationStatus STATUS = new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
            LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 14);

    @Mock private SesV2Client sesClient;

    private TrialEndedNotice notice(String contactEmail) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasenames("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new TrialEndedNotice(sesClient, messages, "noreplay@commercelink.pl", contactEmail);
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("abc123def4");
        store.setName("Mój sklep");
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "2026-10-12T10:00:00Z"));
        return store;
    }

    private SendEmailRequest sent() {
        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(request.capture());
        return request.getValue();
    }

    @Test
    void tellsTheOwnerInPolishWhenTheDataGoes() {
        // when
        notice("kontakt@commercelink.pl").send(store(), STATUS);

        // then
        SendEmailRequest request = sent();
        assertThat(request.fromEmailAddress()).isEqualTo("CommerceLink <noreplay@commercelink.pl>");
        assertThat(request.destination().toAddresses()).containsExactly("owner@example.com");
        assertThat(request.content().simple().subject().data()).isEqualTo("Okres próbny w CommerceLink zakończył się");
        String body = request.content().simple().body().text().data();
        assertThat(body).contains("okres próbny sklepu Mój sklep w CommerceLink zakończył się 12.10.2026");
        assertThat(body).contains("Dane sklepu zostaną trwale usunięte 26.10.2026.");
        assertThat(body).contains("napisz do nas: kontakt@commercelink.pl");
        assertThat(body).endsWith("Zespół CommerceLink");
    }

    @Test
    void leavesTheContactOutWhenNoneIsConfigured() {
        // when
        notice("").send(store(), STATUS);

        // then
        assertThat(sent().content().simple().body().text().data()).doesNotContain("napisz do nas");
    }
}
