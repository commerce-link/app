package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllegroCarrierNamesTest {

    @Test
    void everyIdOfAllegrosOwnNetworkReadsAsOneByAllegro() {
        // when / then
        assertThat(AllegroCarrierNames.displayName("ALLEGRO")).isEqualTo("One by Allegro");
        assertThat(AllegroCarrierNames.displayName("ALLEGRO_ONE_KURIER")).isEqualTo("One by Allegro");
    }

    @Test
    void otherCouriersAreShownAsTheyCome() {
        // when / then
        assertThat(AllegroCarrierNames.displayName("DHL")).isEqualTo("DHL");
        assertThat(AllegroCarrierNames.displayName("DPD")).isEqualTo("DPD");
    }

    @Test
    void noCarrierIdMeansNoName() {
        // when / then
        assertThat(AllegroCarrierNames.displayName(null)).isNull();
        assertThat(AllegroCarrierNames.displayName(" ")).isNull();
    }
}
