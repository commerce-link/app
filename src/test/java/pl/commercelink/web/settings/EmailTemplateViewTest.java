package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.templates.EmailTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateViewTest {

    private static final String PATH = "/dashboard/store/email-templates";

    private static EmailTemplate template(String subject) {
        EmailTemplate template = new EmailTemplate();
        template.setSubject(subject);
        template.setTextBody("Treść");
        return template;
    }

    @Test
    void theStoresOwnCopyWinsOverTheDefault() {
        // when
        EmailTemplateView view = EmailTemplateView.of(EmailNotificationType.ORDER_SHIPPING, true, template("Własny"),
                template("Domyślny"), PATH);

        // then
        assertThat(view.source()).isEqualTo(EmailTemplateView.Source.OWN);
        assertThat(view.subject()).isEqualTo("Własny");
        assertThat(view.editHref()).isEqualTo(PATH + "/ORDER_SHIPPING");
    }

    @Test
    void aSentEmailWithoutAnyContentIsBroken() {
        // when / then
        assertThat(EmailTemplateView.of(EmailNotificationType.ORDER_ASSEMBLY, true, null, null, PATH).broken()).isTrue();
        assertThat(EmailTemplateView.of(EmailNotificationType.ORDER_ASSEMBLY, false, null, null, PATH).broken()).isFalse();
        assertThat(EmailTemplateView.of(EmailNotificationType.ORDER_ASSEMBLY, true, null, template("D"), PATH).broken()).isFalse();
    }

    @Test
    void everyTypeIsListedInTheGroupsOfTheNotificationsPage() {
        // when
        List<EmailTemplateView.Group> groups = EmailTemplateView.groups(type -> false, EmailNotificationType::getTemplateName,
                Map.of(), Map.of(), PATH);

        // then
        assertThat(groups).extracting(EmailTemplateView.Group::labelKey)
                .containsExactly("email.notification.group.orders", "email.notification.group.invoices",
                        "email.notification.group.returns", "email.notification.group.clientVerification");
        assertThat(groups.stream().mapToInt(group -> group.items().size()).sum()).isEqualTo(EmailNotificationType.values().length);
    }

    @Test
    void aSentEmailWhoseOwnCopyLostItsBodyIsBrokenEvenWithADefault() {
        // given
        EmailTemplate withoutBody = new EmailTemplate();
        withoutBody.setSubject("Własny");

        // when
        EmailTemplateView view = EmailTemplateView.of(EmailNotificationType.ORDER_ASSEMBLY, true, withoutBody,
                template("Domyślny"), PATH);

        // then
        assertThat(view.broken()).isTrue();
        assertThat(view.sent()).isFalse();
    }
}
