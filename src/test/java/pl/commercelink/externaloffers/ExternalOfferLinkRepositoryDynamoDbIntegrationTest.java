package pl.commercelink.externaloffers;

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
import pl.commercelink.migration.V025_CreateExternalOfferLinksTable;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offer link queries and conditional saves against DynamoDB Local, on the table as V025 creates it.
 */
@Testcontainers(disabledWithoutDocker = true)
class ExternalOfferLinkRepositoryDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private ExternalOfferLinkRepository repository;

    @BeforeEach
    void createTheTableThroughV025() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        if (client.listTables().getTableNames().contains(ExternalOfferLink.TABLE_NAME)) {
            client.deleteTable(ExternalOfferLink.TABLE_NAME);
        }
        new V025_CreateExternalOfferLinksTable(client).createTable();
        repository = new ExternalOfferLinkRepository(client);
    }

    private static ExternalOfferLink link(String storeId, String marketplace, String key, String ean) {
        return link(storeId, marketplace, key, ean, ExternalOfferLinkSource.MANUAL);
    }

    private static ExternalOfferLink link(String storeId, String marketplace, String key, String ean,
                                             ExternalOfferLinkSource source) {
        return ExternalOfferLink.of(storeId, marketplace, key, List.of(ean), source, "user-1", NOW);
    }

    @Test
    void aSavedLinkIsFoundByItsOfferKey() {
        // given
        repository.save(link("s1", "Empik", "PS-8001", "5900000000001"));

        // when
        ExternalOfferLink found = repository.find("s1", "Empik", "PS-8001").orElseThrow();

        // then
        assertThat(found.getEans()).containsExactly("5900000000001");
        assertThat(found.getOfferKey()).isEqualTo("PS-8001");
        assertThat(found.getSource()).isEqualTo(ExternalOfferLinkSource.MANUAL);
        assertThat(found.getCreatedBy()).isEqualTo("user-1");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(repository.find("s1", "Empik", "ps-8001")).isEmpty();
        assertThat(repository.find("s2", "Empik", "PS-8001")).isEmpty();
        assertThat(repository.find("s1", "Allegro", "PS-8001")).isEmpty();
    }

    @Test
    void savingTheSameOfferKeyAgainReplacesTheLink() {
        // given
        repository.save(link("s1", "Empik", "PS-8001", "5900000000001"));

        // when
        repository.save(link("s1", "Empik", "PS-8001", "5900000000002"));

        // then
        assertThat(repository.find("s1", "Empik", "PS-8001")).map(ExternalOfferLink::getEans).contains(List.of("5900000000002"));
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
        List<ExternalOfferLink> links = repository.findAll("s1", "Empik");

        // then: the "Empik#" prefix keeps a marketplace whose name starts with "Empik" out
        assertThat(links).extracting(ExternalOfferLink::getOfferKey).containsExactlyInAnyOrder("A-1", "B-2");
    }

    @Test
    void aMultiEanListRoundTripsInOrder() {
        // given
        repository.save(ExternalOfferLink.of("s1", "Empik", "K-1", List.of("5900000000009", "5900000000001"),
                ExternalOfferLinkSource.EAN, null, NOW));

        // when
        ExternalOfferLink found = repository.find("s1", "Empik", "K-1").orElseThrow();

        // then
        assertThat(found.getEans()).containsExactly("5900000000009", "5900000000001");
    }

    @Test
    void saveUnlessManualWritesWhenTheKeyHasNoLink() {
        // when
        boolean saved = repository.saveUnlessManual(link("s1", "Empik", "K-1", "5900000000001", ExternalOfferLinkSource.EAN));

        // then
        assertThat(saved).isTrue();
        assertThat(repository.find("s1", "Empik", "K-1")).map(ExternalOfferLink::getEans).contains(List.of("5900000000001"));
    }

    @Test
    void saveUnlessManualOverwritesAnEanLink() {
        // given
        repository.save(link("s1", "Empik", "K-1", "5900000000001", ExternalOfferLinkSource.EAN));

        // when
        boolean saved = repository.saveUnlessManual(link("s1", "Empik", "K-1", "5900000000002", ExternalOfferLinkSource.EAN));

        // then
        assertThat(saved).isTrue();
        assertThat(repository.find("s1", "Empik", "K-1")).map(ExternalOfferLink::getEans).contains(List.of("5900000000002"));
    }

    @Test
    void saveUnlessManualRefusesToTouchAManualLink() {
        // given
        repository.save(link("s1", "Empik", "K-1", "5900000000001", ExternalOfferLinkSource.MANUAL));

        // when
        boolean saved = repository.saveUnlessManual(link("s1", "Empik", "K-1", "5900000000002", ExternalOfferLinkSource.EAN));

        // then
        assertThat(saved).isFalse();
        assertThat(repository.find("s1", "Empik", "K-1")).map(ExternalOfferLink::getEans).contains(List.of("5900000000001"));
    }
}
