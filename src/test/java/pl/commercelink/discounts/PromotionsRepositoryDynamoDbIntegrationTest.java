package pl.commercelink.discounts;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PromotionsRepositoryDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    private PromotionsRepository repository;

    @BeforeEach
    void createTable() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        if (client.listTables().getTableNames().contains(Promotion.TABLE_NAME)) {
            client.deleteTable(Promotion.TABLE_NAME);
        }
        client.createTable(mapper.generateCreateTableRequest(Promotion.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        repository = new PromotionsRepository(client);
    }

    @Test
    void savedPromotionIsFoundWithAllItsTerms() {
        // given
        Promotion original = promotion("store-1", "Monitor + kabel taniej");
        original.setConditionTarget(DiscountTarget.Category);
        original.setConditionValues(List.of(new TargetValue("Monitory", "Monitory")));
        original.setConditionQuantity(2);
        original.setMinOrderValue(500);
        original.setTarget(DiscountTarget.Product);
        original.setTargetValues(List.of(new TargetValue("PIM-1", "Kabel HDMI 2 m")));
        original.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        repository.save(original);

        // when
        Promotion stored = repository.find("store-1", original.getPromotionId()).orElseThrow();

        // then
        assertThat(stored.getName()).isEqualTo("Monitor + kabel taniej");
        assertThat(stored.getConditionTarget()).isEqualTo(DiscountTarget.Category);
        assertThat(stored.getConditionValues()).extracting(TargetValue::getId).containsExactly("Monitory");
        assertThat(stored.getConditionQuantity()).isEqualTo(2);
        assertThat(stored.getMinOrderValue()).isEqualTo(500);
        assertThat(stored.getType()).isEqualTo(DiscountType.Percentage);
        assertThat(stored.getTarget()).isEqualTo(DiscountTarget.Product);
        assertThat(stored.getTargetValues()).extracting(TargetValue::getName).containsExactly("Kabel HDMI 2 m");
        assertThat(stored.getValidTo()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        assertThat(stored.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 14));
    }

    @Test
    void findIgnoresIdsThatAreNoPromotionIds() {
        // given
        repository.save(promotion("store-1", "Tydzień marki ASUS"));

        // when / then
        assertThat(repository.find("store-1", "x".repeat(2000))).isEmpty();
        assertThat(repository.find("store-1", "new")).isEmpty();
        assertThat(repository.find("store-1", null)).isEmpty();
        assertThat(repository.find("store-1", UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void findAllReturnsOnlyThePromotionsOfTheStore() {
        // given
        repository.save(promotion("store-1", "Tydzień marki ASUS"));
        repository.save(promotion("store-1", "Duży koszyk"));
        repository.save(promotion("store-2", "Inny sklep"));

        // when
        List<Promotion> promotions = repository.findAll("store-1");

        // then
        assertThat(promotions).extracting(Promotion::getName)
                .containsExactlyInAnyOrder("Tydzień marki ASUS", "Duży koszyk");
    }

    @Test
    void setActiveKeepsOtherFields() {
        // given
        Promotion original = promotion("store-1", "Duży koszyk");
        original.setType(DiscountType.Amount);
        original.setValue(150);
        original.setMinOrderValue(5000);
        repository.save(original);

        // when
        repository.setActive("store-1", original.getPromotionId(), false);

        // then
        Promotion stored = repository.find("store-1", original.getPromotionId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getName()).isEqualTo("Duży koszyk");
        assertThat(stored.getType()).isEqualTo(DiscountType.Amount);
        assertThat(stored.getValue()).isEqualTo(150);
        assertThat(stored.getMinOrderValue()).isEqualTo(5000);
        assertThat(stored.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 14));
    }

    @Test
    void setActiveOfAMissingPromotionCreatesNothing() {
        // when
        repository.setActive("store-1", UUID.randomUUID().toString(), true);

        // then
        assertThat(repository.findAll("store-1")).isEmpty();
    }

    @Test
    void deleteRemovesThePromotion() {
        // given
        Promotion promotion = promotion("store-1", "Tydzień marki ASUS");
        repository.save(promotion);

        // when
        repository.delete(promotion);

        // then
        assertThat(repository.find("store-1", promotion.getPromotionId())).isEmpty();
    }

    private static Promotion promotion(String storeId, String name) {
        Promotion promotion = new Promotion();
        promotion.setStoreId(storeId);
        promotion.setPromotionId(UUID.randomUUID().toString());
        promotion.setName(name);
        promotion.setType(DiscountType.Percentage);
        promotion.setValue(10);
        promotion.setActive(true);
        promotion.setCreatedAt(LocalDateTime.of(2026, 11, 12, 10, 14));
        return promotion;
    }
}
