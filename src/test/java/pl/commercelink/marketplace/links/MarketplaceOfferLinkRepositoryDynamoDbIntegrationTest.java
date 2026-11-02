package pl.commercelink.marketplace.links;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.migration.V024_CreateMarketplaceOfferLinksTable;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offer link queries against DynamoDB Local, on the table as V024 creates it. The pimId index is created together
 * with the table, so DynamoDB Local maintains it on every write (unlike an index added to a filled table, which it
 * backfills in the background) and the index read needs no waiting.
 */
@Testcontainers(disabledWithoutDocker = true)
class MarketplaceOfferLinkRepositoryDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private MarketplaceOfferLinkRepository repository;

    @BeforeEach
    void createTheTableThroughV024() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        if (client.listTables().getTableNames().contains(MarketplaceOfferLink.TABLE_NAME)) {
            client.deleteTable(MarketplaceOfferLink.TABLE_NAME);
        }
        new V024_CreateMarketplaceOfferLinksTable(client).createTable();
        repository = new MarketplaceOfferLinkRepository(client);
    }

    private static MarketplaceOfferLink link(String storeId, String marketplace, String key, String pimId) {
        return MarketplaceOfferLink.of(storeId, marketplace, key, pimId, MarketplaceOfferLinkSource.MANUAL, "user-1", NOW);
    }

    @Test
    void aSavedLinkIsFoundByItsOfferKey() {
        // given
        repository.save(link("s1", "Empik", "SILLO-84980", "pim-1"));

        // when
        MarketplaceOfferLink found = repository.find("s1", "Empik", "SILLO-84980").orElseThrow();

        // then
        assertThat(found.getPimId()).isEqualTo("pim-1");
        assertThat(found.getSellerOfferKey()).isEqualTo("SILLO-84980");
        assertThat(found.getSource()).isEqualTo(MarketplaceOfferLinkSource.MANUAL);
        assertThat(found.getCreatedBy()).isEqualTo("user-1");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(repository.find("s1", "Empik", "sillo-84980")).isEmpty();
        assertThat(repository.find("s2", "Empik", "SILLO-84980")).isEmpty();
        assertThat(repository.find("s1", "Allegro", "SILLO-84980")).isEmpty();
    }

    @Test
    void savingTheSameOfferKeyAgainReplacesTheLink() {
        // given
        repository.save(link("s1", "Empik", "SILLO-84980", "pim-1"));

        // when
        repository.save(link("s1", "Empik", "SILLO-84980", "pim-2"));

        // then
        assertThat(repository.find("s1", "Empik", "SILLO-84980")).map(MarketplaceOfferLink::getPimId).contains("pim-2");
        assertThat(repository.findAll("s1", "Empik")).hasSize(1);
    }

    @Test
    void findAllReturnsOnlyTheStoresLinksOfThatMarketplace() {
        // given
        repository.save(link("s1", "Empik", "A-1", "pim-1"));
        repository.save(link("s1", "Empik", "B-2", "pim-2"));
        repository.save(link("s1", "EmpikX", "C-3", "pim-3"));
        repository.save(link("s1", "Allegro", "D-4", "pim-4"));
        repository.save(link("s2", "Empik", "E-5", "pim-5"));

        // when
        List<MarketplaceOfferLink> links = repository.findAll("s1", "Empik");

        // then: the "Empik#" prefix keeps a marketplace whose name starts with "Empik" out
        assertThat(links).extracting(MarketplaceOfferLink::getSellerOfferKey).containsExactlyInAnyOrder("A-1", "B-2");
    }

    @Test
    void saveIfAbsentWritesANewLink() {
        // when
        boolean saved = repository.saveIfAbsent(link("s1", "Empik", "SILLO-84980", "pim-1"));

        // then
        assertThat(saved).isTrue();
        assertThat(repository.find("s1", "Empik", "SILLO-84980")).map(MarketplaceOfferLink::getPimId).contains("pim-1");
    }

    @Test
    void saveIfAbsentLeavesAnExistingLinkAlone() {
        // given
        repository.save(link("s1", "Empik", "SILLO-84980", "pim-manual"));

        // when
        boolean saved = repository.saveIfAbsent(link("s1", "Empik", "SILLO-84980", "pim-ean"));

        // then
        assertThat(saved).isFalse();
        assertThat(repository.find("s1", "Empik", "SILLO-84980")).map(MarketplaceOfferLink::getPimId).contains("pim-manual");
    }

    @Test
    void findByPimIdReadsTheStoresLinksThroughTheIndex() {
        // given
        repository.save(link("s1", "Empik", "A-1", "pim-1"));
        repository.save(link("s1", "Allegro", "B-2", "pim-1"));
        repository.save(link("s1", "Empik", "C-3", "pim-2"));
        repository.save(link("s2", "Empik", "D-4", "pim-1"));

        // when
        List<MarketplaceOfferLink> links = repository.findByPimId("s1", "pim-1");

        // then
        assertThat(links).extracting(MarketplaceOfferLink::getLinkKey).containsExactlyInAnyOrder("Empik#A-1", "Allegro#B-2");
    }
}
