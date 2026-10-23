package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WarehouseRepositoryTest {

    private final AmazonDynamoDB amazonDynamoDB = mock(AmazonDynamoDB.class);
    private final WarehouseRepository repository = new WarehouseRepository(amazonDynamoDB);

    @Test
    void findAllAvailableByMfnsReturnsEmptyWithoutQueryingForEmptyMfns() {
        // given / when
        List<WarehouseItem> result = repository.findAllAvailableByMfns("store-1", List.of());

        // then
        assertThat(result).isEmpty();
        verify(amazonDynamoDB, never()).scan(any(ScanRequest.class));
    }

    @Test
    void findAllByMfnsReturnsEmptyWithoutQueryingForEmptyMfns() {
        // given / when
        List<WarehouseItem> result = repository.findAllByMfns("store-1", List.of());

        // then
        assertThat(result).isEmpty();
        verify(amazonDynamoDB, never()).scan(any(ScanRequest.class));
    }
}
