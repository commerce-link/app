package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V024_RenameBasketItemMfnToSkuTest {

    @Mock
    private AmazonDynamoDB dynamoDB;

    @InjectMocks
    private V024_RenameBasketItemMfnToSku migration;

    @Test
    void migrationMovesTheMfnOfEveryItemToSku() {
        // given
        scanReturns(basket("basket-1", 3, item("Dysk", "mfn", "MZ-V9P1T0BW"), item("Dostawa", "mfn", "Shipping")));

        // when
        migration.execute();

        // then
        ArgumentCaptor<UpdateItemRequest> update = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(update.capture());
        List<AttributeValue> items = update.getValue().getExpressionAttributeValues().get(":items").getL();
        assertThat(items).extracting(i -> i.getM().get("sku").getS()).containsExactly("MZ-V9P1T0BW", "Shipping");
        assertThat(items).allSatisfy(i -> assertThat(i.getM()).doesNotContainKey("mfn").containsKey("name"));
        assertThat(update.getValue().getKey()).isEqualTo(Map.of(
                "storeId", new AttributeValue("store-1"), "basketId", new AttributeValue("basket-1")));
    }

    @Test
    void basketIsRewrittenOnlyAtTheVersionTheScanSaw() {
        // given
        scanReturns(basket("basket-1", 3, item("Dysk", "mfn", "MZ-V9P1T0BW")));

        // when
        migration.execute();

        // then
        ArgumentCaptor<UpdateItemRequest> update = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(update.capture());
        assertThat(update.getValue().getConditionExpression()).isEqualTo("attribute_exists(basketId) AND #v = :v");
        assertThat(update.getValue().getExpressionAttributeValues().get(":v").getN()).isEqualTo("3");
    }

    @Test
    void basketWithoutVersionIsRewrittenOnlyWhileItHasNone() {
        // given
        Map<String, AttributeValue> unversioned = new HashMap<>(basket("basket-1", 0, item("Dysk", "mfn", "M-1")));
        unversioned.remove("version");
        scanReturns(unversioned);

        // when
        migration.execute();

        // then
        ArgumentCaptor<UpdateItemRequest> update = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(update.capture());
        assertThat(update.getValue().getConditionExpression()).isEqualTo("attribute_exists(basketId) AND attribute_not_exists(#v)");
    }

    @Test
    void basketChangedSinceTheScanIsLeftAsItIs() {
        // given
        scanReturns(basket("basket-1", 3, item("Dysk", "mfn", "MZ-V9P1T0BW")));
        when(dynamoDB.updateItem(any(UpdateItemRequest.class))).thenThrow(new ConditionalCheckFailedException("changed"));

        // when / then
        assertThatCode(() -> migration.execute()).doesNotThrowAnyException();
    }

    @Test
    void basketWithoutAnyMfnIsNotWritten() {
        // given
        scanReturns(basket("basket-1", 3, item("Dysk", "sku", "5901234123457")));

        // when
        migration.execute();

        // then
        verify(dynamoDB, never()).updateItem(any(UpdateItemRequest.class));
    }

    private void scanReturns(Map<String, AttributeValue> basket) {
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(basket)));
    }

    private static Map<String, AttributeValue> basket(String basketId, long version, AttributeValue... items) {
        return Map.of(
                "storeId", new AttributeValue("store-1"),
                "basketId", new AttributeValue(basketId),
                "version", new AttributeValue().withN(String.valueOf(version)),
                "basketItems", new AttributeValue().withL(items));
    }

    private static AttributeValue item(String name, String codeAttribute, String code) {
        return new AttributeValue().withM(Map.of("name", new AttributeValue(name), codeAttribute, new AttributeValue(code)));
    }
}
