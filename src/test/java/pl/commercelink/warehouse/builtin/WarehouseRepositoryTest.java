package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void findAllCategoriesKeepsCategoryKeysOutsideTheEnum() {
        // given
        ScanResult scanResult = new ScanResult().withItems(
                Map.of("category", new AttributeValue("Smartwatches")),
                Map.of("category", new AttributeValue("CPU")));
        when(amazonDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResult);

        // when
        Set<String> categories = repository.findAllCategories("store-1");

        // then
        assertThat(categories).containsExactlyInAnyOrder("Smartwatches", "CPU");
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
