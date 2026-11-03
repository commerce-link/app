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
 * The offer link queries against DynamoDB Local, on the table as V024 creates it. 
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

    private static MarketplaceOfferLink link(String storeId, String marketplace, String key, String ean) {
        return link(storeId, marketplace, key, ean, MarketplaceOfferLinkSource.MANUAL);
    }

    private static MarketplaceOfferLink link(String storeId, String marketplace, String key, String ean,
                                             MarketplaceOfferLinkSource source) {
        return MarketplaceOfferLink.of(storeId, marketplace, key, List.of(ean), source, "user-1", NOW);
    }

    @Test
    void aSavedLinkIsFoundByItsOfferKey() {
        // given
        repository.save(link("s1", "Empik", "SILLO-84980", "5900000000001"));

        // when
        MarketplaceOfferLink found = repository.find("s1", "Empik", "SILLO-84980").orElseThrow();

        // then
        assertThat(found.getEans()).containsExactly("5900000000001");
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
        repository.save(link("s1", "Empik", "SILLO-84980", "5900000000001"));

        // when
        repository.save(link("s1", "Empik", "SILLO-84980", "5900000000002"));

        // then
        assertThat(repository.find("s1", "Empik", "SILLO-84980")).map(MarketplaceOfferLink::getEans).contains(List.of("5900000000002"));
        assertThat(repository.findAll("s1", "Empik")).hasSize(1);
    }

    @Test
    void findAllReturnsOnlyTheStoresLinksOfThatMarketplace() {
        // given
        repository.save(link("s1", "Empik", "A-1", "5900000000001"));
        repository.save(link("s1", "Empik", "B-2", "5900000000002"));
        repository.save(link("s1", "EmpikX", "C-3", "5900000000003"));
        repository.save(link("s1", "Allegro", "D-4", "5900000000004"));
        repository.save(link("s2", "Empik", "E-5", "5900000000005"));

        // when
        List<MarketplaceOfferLink> links = repository.findAll("s1", "Empik");

        // then: the "Empik#" prefix keeps a marketplace whose name starts with "Empik" out
        assertThat(links).extracting(MarketplaceOfferLink::getSellerOfferKey).containsExactlyInAnyOrder("A-1", "B-2");
    }

    @Test
    void aMultiEanListRoundTripsInOrder() {
        // given
        repository.save(MarketplaceOfferLink.of("s1", "Empik", "K-1", List.of("5900000000009", "5900000000001"),
                MarketplaceOfferLinkSource.EAN, null, NOW));

        // when
        MarketplaceOfferLink found = repository.find("s1", "Empik", "K-1").orElseThrow();

        // then
        assertThat(found.getEans()).containsExactly("5900000000009", "5900000000001");
    }

    @Test
    void saveUnlessManualWritesWhenTheKeyHasNoLink() {
        // when
        boolean saved = repository.saveUnlessManual(link("s1", "Empik", "K-1", "5900000000001", MarketplaceOfferLinkSource.EAN));

        // then
        assertThat(saved).isTrue();
        assertThat(repository.find("s1", "Empik", "K-1")).map(MarketplaceOfferLink::getEans).contains(List.of("5900000000001"));
    }

    @Test
    void saveUnlessManualOverwritesAnEanLink() {
        // given
        repository.save(link("s1", "Empik", "K-1", "5900000000001", MarketplaceOfferLinkSource.EAN));

        // when
        boolean saved = repository.saveUnlessManual(link("s1", "Empik", "K-1", "5900000000002", MarketplaceOfferLinkSource.EAN));

        // then
        assertThat(saved).isTrue();
        assertThat(repository.find("s1", "Empik", "K-1")).map(MarketplaceOfferLink::getEans).contains(List.of("5900000000002"));
    }

    @Test
    void saveUnlessManualRefusesToTouchAManualLink() {
        // given
        repository.save(link("s1", "Empik", "K-1", "5900000000001", MarketplaceOfferLinkSource.MANUAL));

        // when
        boolean saved = repository.saveUnlessManual(link("s1", "Empik", "K-1", "5900000000002", MarketplaceOfferLinkSource.EAN));

        // then
        assertThat(saved).isFalse();
        assertThat(repository.find("s1", "Empik", "K-1")).map(MarketplaceOfferLink::getEans).contains(List.of("5900000000001"));
    }
}
