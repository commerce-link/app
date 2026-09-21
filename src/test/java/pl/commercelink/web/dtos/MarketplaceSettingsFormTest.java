package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceSettingsFormTest {

    @Test
    void schedulesArePostedPerMarketplaceSoOnlyTheChosenOnesAreRead() {
        // given
        MarketplaceSettingsForm form = new MarketplaceSettingsForm();
        form.setProviderName("Allegro");
        form.getSchedules().put("Allegro.orders", " 0/30 * * * ? * ");
        form.getSchedules().put("CsCart.orders", "0 5 * * ? *");

        // when / then
        assertThat(MarketplaceSettingsForm.scheduleName("Allegro", MarketplaceSettingsForm.ORDERS)).isEqualTo("schedules[Allegro.orders]");
        assertThat(form.ordersSchedule()).isEqualTo("0/30 * * * ? *");
        assertThat(form.returnsSchedule()).isNull();
    }

    @Test
    void anEmptyScheduleMeansTheDefaultAndIsValid() {
        // given
        MarketplaceSettingsForm form = new MarketplaceSettingsForm();
        form.setProviderName("Allegro");
        form.getSchedules().put("Allegro.orders", "");

        // when / then
        assertThat(form.validateSchedules(true, 15)).isEmpty();
    }

    @Test
    void aTooFrequentOrMalformedScheduleIsReportedAtItsField() {
        // given
        MarketplaceSettingsForm form = new MarketplaceSettingsForm();
        form.setProviderName("Allegro");
        form.getSchedules().put("Allegro.orders", "0/5 * * * ? *");
        form.getSchedules().put("Allegro.returns", "not a schedule");

        // when / then
        assertThat(form.validateSchedules(true, 15))
                .containsEntry("schedule-Allegro-orders", "store.marketplace.schedule.tooFrequent")
                .containsEntry("schedule-Allegro-returns", "store.marketplace.schedule.invalid");
        assertThat(form.validateSchedules(false, 15)).containsOnlyKeys("schedule-Allegro-orders");
    }
}
