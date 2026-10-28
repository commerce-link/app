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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
class DiscountCodesRepositoryDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    private DynamoDBMapper mapper;
    private DiscountCodesRepository repository;

    @BeforeEach
    void createTable() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        if (client.listTables().getTableNames().contains(DiscountCode.TABLE_NAME)) {
            client.deleteTable(DiscountCode.TABLE_NAME);
        }
        client.createTable(mapper.generateCreateTableRequest(DiscountCode.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        repository = new DiscountCodesRepository(client);
    }

    @Test
    void createRefusesExistingCode() {
        // given
        repository.create(code("store-1", "LATO-10", "Kampania letnia"));

        // when
        boolean created = repository.create(code("store-1", " lato-10 ", "Inna kampania"));

        // then
        assertThat(created).isFalse();
        assertThat(repository.find("store-1", "LATO-10")).get()
                .extracting(DiscountCode::getName).isEqualTo("Kampania letnia");
    }

    @Test
    void createStoresTheCodeNormalized() {
        // when
        boolean created = repository.create(code("store-1", " lato-10 ", "Kampania letnia"));

        // then
        assertThat(created).isTrue();
        assertThat(repository.findAll("store-1")).extracting(DiscountCode::getCode).containsExactly("LATO-10");
    }

    @Test
    void findNormalizesCode() {
        // given
        repository.create(code("store-1", "LATO-10", "Kampania letnia"));

        // when / then
        assertThat(repository.find("store-1", " lato-10 ")).get().extracting(DiscountCode::getCode).isEqualTo("LATO-10");
        assertThat(repository.find("store-1", "ZIMA-10")).isEmpty();
        assertThat(repository.find("store-1", "  ")).isEmpty();
        assertThat(repository.find("store-1", null)).isEmpty();
    }

    @Test
    void findReturnsEmptyForMalformedCode() {
        // given
        repository.create(code("store-1", "LATO-10", "Kampania letnia"));

        // when / then
        assertThat(repository.find("store-1", "L".repeat(2000))).isEmpty();
        assertThat(repository.find("store-1", "LATO_10")).isEmpty();
    }

    @Test
    void findAllReturnsOnlyStoreCodesInCodeOrder() {
        // given
        repository.create(code("store-1", "WITAJ-5", "Powitalny"));
        repository.create(code("store-1", "BF-2026", "Black Friday"));
        repository.create(code("store-2", "LATO-10", "Inny sklep"));

        // when
        List<DiscountCode> codes = repository.findAll("store-1");

        // then
        assertThat(codes).extracting(DiscountCode::getCode).containsExactly("BF-2026", "WITAJ-5");
    }

    @Test
    void setActiveKeepsOtherFields() {
        // given
        DiscountCode original = code("store-1", "GPU-50", "Rabat na podzespoły");
        original.setType(DiscountType.Amount);
        original.setValue(50);
        original.setTarget(DiscountTarget.Category);
        original.setTargetValues(List.of(new TargetValue("Karty graficzne", "Karty graficzne")));
        original.setPaymentProvider("paynow");
        original.setMinOrderValue(500);
        original.setMaxUses(100);
        original.setValidTo(LocalDateTime.of(2026, 11, 30, 23, 59));
        repository.create(original);

        // when
        repository.setActive("store-1", "gpu-50", false);

        // then
        DiscountCode stored = repository.find("store-1", "GPU-50").orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getName()).isEqualTo("Rabat na podzespoły");
        assertThat(stored.getType()).isEqualTo(DiscountType.Amount);
        assertThat(stored.getValue()).isEqualTo(50);
        assertThat(stored.getTarget()).isEqualTo(DiscountTarget.Category);
        assertThat(stored.getTargetValues()).extracting(TargetValue::getId).containsExactly("Karty graficzne");
        assertThat(stored.getPaymentProvider()).isEqualTo("paynow");
        assertThat(stored.getMinOrderValue()).isEqualTo(500);
        assertThat(stored.getMaxUses()).isEqualTo(100);
        assertThat(stored.getValidTo()).isEqualTo(LocalDateTime.of(2026, 11, 30, 23, 59));
    }

    @Test
    void setActiveKeepsUsedCount() {
        // given
        repository.create(code("store-1", "GPU-50", "Rabat na podzespoły"));
        repository.redeem("store-1", "GPU-50");

        // when
        repository.setActive("store-1", "GPU-50", false);

        // then
        assertThat(repository.find("store-1", "GPU-50")).get().extracting(DiscountCode::getUsedCount).isEqualTo(1);
    }

    @Test
    void redeemCountsPastTheLimit() {
        // given
        DiscountCode limited = code("store-1", "WITAJ-5", "Powitalny");
        limited.setMaxUses(2);
        repository.create(limited);

        // when
        DiscountCode first = repository.redeem("store-1", "witaj-5").orElseThrow();
        DiscountCode second = repository.redeem("store-1", "WITAJ-5").orElseThrow();
        DiscountCode third = repository.redeem("store-1", "WITAJ-5").orElseThrow();

        // then
        assertThat(List.of(first, second, third)).extracting(DiscountCode::getUsedCount, DiscountCode::isOverused)
                .containsExactly(tuple(1, false), tuple(2, false), tuple(3, true));
        assertThat(repository.find("store-1", "WITAJ-5")).get().extracting(DiscountCode::getUsedCount).isEqualTo(3);
    }

    @Test
    void redeemWithoutLimitAlwaysCounts() {
        // given
        repository.create(code("store-1", "BF-2026", "Black Friday"));

        // when
        repository.redeem("store-1", "BF-2026");
        repository.redeem("store-1", "BF-2026");
        DiscountCode third = repository.redeem("store-1", "BF-2026").orElseThrow();

        // then
        assertThat(third.getUsedCount()).isEqualTo(3);
        assertThat(third.isOverused()).isFalse();
        assertThat(repository.find("store-1", "BF-2026")).get().extracting(DiscountCode::getUsedCount).isEqualTo(3);
    }

    @Test
    void redeemReturnsTheWholeCode() {
        // given
        DiscountCode original = code("store-1", "GPU-50", "Rabat na podzespoły");
        original.setMaxUses(100);
        original.setValidTo(LocalDateTime.of(2026, 11, 30, 23, 59));
        repository.create(original);

        // when
        DiscountCode redeemed = repository.redeem("store-1", "GPU-50").orElseThrow();

        // then
        assertThat(redeemed.getStoreId()).isEqualTo("store-1");
        assertThat(redeemed.getCode()).isEqualTo("GPU-50");
        assertThat(redeemed.getMaxUses()).isEqualTo(100);
        assertThat(redeemed.getValidTo()).isEqualTo(LocalDateTime.of(2026, 11, 30, 23, 59));
    }

    @Test
    void redeemOfMissingCodeCreatesNothing() {
        // when
        Optional<DiscountCode> redeemed = repository.redeem("store-1", "GONE-10");

        // then
        assertThat(redeemed).isEmpty();
        assertThat(repository.findAll("store-1")).isEmpty();
    }

    @Test
    void setActiveOfMissingCodeCreatesNothing() {
        // when
        repository.setActive("store-1", "GONE-10", true);

        // then
        assertThat(repository.findAll("store-1")).isEmpty();
    }

    @Test
    void deleteRefusedAfterUse() {
        // given
        DiscountCode used = code("store-1", "WITAJ-5", "Powitalny");
        used.setUsedCount(1);
        mapper.save(used);

        // when
        boolean deleted = repository.delete("store-1", "WITAJ-5");

        // then
        assertThat(deleted).isFalse();
        assertThat(repository.find("store-1", "WITAJ-5")).isPresent();
    }

    @Test
    void deleteRemovesUnusedCode() {
        // given
        repository.create(code("store-1", "BF-2026", "Black Friday"));

        // when
        boolean deleted = repository.delete("store-1", "bf-2026");

        // then
        assertThat(deleted).isTrue();
        assertThat(repository.find("store-1", "BF-2026")).isEmpty();
    }

    private static DiscountCode code(String storeId, String code, String name) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId(storeId);
        discountCode.setCode(code);
        discountCode.setName(name);
        discountCode.setType(DiscountType.Percentage);
        discountCode.setValue(10);
        discountCode.setActive(true);
        return discountCode;
    }
}
