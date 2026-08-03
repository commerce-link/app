package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyCategoryMatchPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(TaxonomyCategoryMatchProperties.class)
    static class Config {
    }

    @Test
    void bindsAllPropertiesIncludingNestedMappingGroup() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues(
                "taxonomy.category-match.buckets=7",
                "taxonomy.category-match.pending-cap=1000",
                "taxonomy.category-match.mapping.min-samples=3",
                "taxonomy.category-match.mapping.min-share=0.8",
                "taxonomy.category-match.mapping.min-confidence=0.7",
                "taxonomy.category-match.mapping.trickle-every=10",
                "taxonomy.category-match.max-attempts=2");

        // when / then
        configured.run(context -> {
            TaxonomyCategoryMatchProperties properties = context.getBean(TaxonomyCategoryMatchProperties.class);
            assertThat(properties.buckets()).isEqualTo(7);
            assertThat(properties.pendingCap()).isEqualTo(1000);
            assertThat(properties.mapping().minSamples()).isEqualTo(3);
            assertThat(properties.mapping().minShare()).isEqualTo(0.8);
            assertThat(properties.mapping().minConfidence()).isEqualTo(0.7);
            assertThat(properties.mapping().trickleEvery()).isEqualTo(10);
            assertThat(properties.maxAttempts()).isEqualTo(2);
        });
    }

    @Test
    void appliesDefaultsWhenNothingIsConfigured() {
        // when / then
        runner.run(context -> {
            TaxonomyCategoryMatchProperties properties = context.getBean(TaxonomyCategoryMatchProperties.class);
            assertThat(properties.buckets()).isEqualTo(100);
            assertThat(properties.pendingCap()).isEqualTo(300000);
            assertThat(properties.mapping().minSamples()).isEqualTo(5);
            assertThat(properties.mapping().minShare()).isEqualTo(0.9);
            assertThat(properties.mapping().minConfidence()).isEqualTo(0.9);
            assertThat(properties.mapping().trickleEvery()).isEqualTo(20);
            assertThat(properties.maxAttempts()).isEqualTo(4);
        });
    }

    @Test
    void invalidPropertyValueFailsContextStartup() {
        // when / then
        runner.withPropertyValues("taxonomy.category-match.max-attempts=-1")
                .run(context -> assertThat(context).hasFailed());
    }
}
