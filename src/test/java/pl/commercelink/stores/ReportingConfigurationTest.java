package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingConfigurationTest {

    @Test
    void enablingGoogleAdsForTheFirstTimeCreatesTheConversionsToken() {
        // given
        ReportingConfiguration configuration = new ReportingConfiguration();

        // when
        configuration.enableGoogleAds();

        // then
        assertThat(configuration.isGoogleAdsEnabled()).isTrue();
        assertThat(configuration.getGoogleAdsToken()).isNotBlank();
    }

    @Test
    void enablingGoogleAdsAgainKeepsTheTokenPastedIntoGoogleAds() {
        // given
        ReportingConfiguration configuration = new ReportingConfiguration();
        configuration.enableGoogleAds();
        String token = configuration.getGoogleAdsToken();

        // when
        configuration.enableGoogleAds();

        // then
        assertThat(configuration.getGoogleAdsToken()).isEqualTo(token);
    }

    @Test
    void disablingGoogleAdsKeepsTheTokenSoReEnablingRestoresTheSameAddress() {
        // given
        ReportingConfiguration configuration = new ReportingConfiguration();
        configuration.enableGoogleAds();
        String token = configuration.getGoogleAdsToken();

        // when
        configuration.disableGoogleAds();
        configuration.enableGoogleAds();

        // then
        assertThat(configuration.getGoogleAdsToken()).isEqualTo(token);
    }

    @Test
    void disabledGoogleAdsKeepsTheTokenButIsNotEnabled() {
        // given
        ReportingConfiguration configuration = new ReportingConfiguration();
        configuration.enableGoogleAds();

        // when
        configuration.disableGoogleAds();

        // then
        assertThat(configuration.isGoogleAdsEnabled()).isFalse();
        assertThat(configuration.getGoogleAdsToken()).isNotBlank();
    }

    @Test
    void regeneratingTheTokenReplacesTheOldAddress() {
        // given
        ReportingConfiguration configuration = new ReportingConfiguration();
        configuration.enableGoogleAds();
        String token = configuration.getGoogleAdsToken();

        // when
        configuration.regenerateGoogleAdsToken();

        // then
        assertThat(configuration.getGoogleAdsToken()).isNotBlank().isNotEqualTo(token);
    }
}
