package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AwaitingPickupsRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private AwaitingPickupsRepository repository;

    @BeforeEach
    void setup() {
        repository = new AwaitingPickupsRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    void deleteRemovesTheEntryByStoreAndExternalId() {
        // given
        ArgumentCaptor<AwaitingPickup> key = ArgumentCaptor.forClass(AwaitingPickup.class);

        // when
        repository.delete("store-1", "21480003");

        // then
        verify(dynamoDBMapper).delete(key.capture());
        assertThat(key.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(key.getValue().getExternalId()).isEqualTo("21480003");
    }
}
