package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V010_BackfillCategoryDefinitionPimCategoryIdsTest {

    @Mock
    private AmazonDynamoDB dynamoDB;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private Environment environment;
    @InjectMocks
    private V010_BackfillCategoryDefinitionPimCategoryIds migration;

    private static AttributeValue string(String value) {
        return new AttributeValue().withS(value);
    }

    private static AttributeValue definitionWith(Map<String, AttributeValue> attributes) {
        return new AttributeValue().withM(attributes);
    }

    private void stubTreeWithInternalNodeSharingLeafName() {
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("8760", "1", "Telewizory", "pl"),
                new PimCategory("2001", "8760", "Telewizory OLED", "pl"),
                new PimCategory("1584", "1", "Telewizory", "pl"),
                new PimCategory("1", null, "RTV", "pl")));
    }

    @Test
    void resolvesNameCollidingBetweenLeafAndInternalNodeToTheLeaf() {
        // given — internal node 8760 comes FIRST so a naive all-levels putIfAbsent map catches the wrong id
        stubTreeWithInternalNodeSharingLeafName();
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "catalogId", string("catalog-1"),
                "categories", new AttributeValue().withL(definitionWith(new HashMap<>(Map.of(
                        "categoryId", string("def-1"),
                        "category", string("Telewizory"))))));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.backfillPimCategoryIds();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        assertEquals("store-1", captor.getValue().getKey().get("storeId").getS());
        assertEquals("catalog-1", captor.getValue().getKey().get("catalogId").getS());
        Map<String, AttributeValue> converted = captor.getValue().getExpressionAttributeValues()
                .get(":categories").getL().getFirst().getM();
        assertEquals("1584", converted.get("pimCategoryIds").getL().getFirst().getS());
        assertEquals("Telewizory", converted.get("category").getS());
    }

    @Test
    void unresolvableNameLeavesTheDefinitionUntouchedAndWritesNothing() {
        // given
        stubTreeWithInternalNodeSharingLeafName();
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "catalogId", string("catalog-1"),
                "categories", new AttributeValue().withL(definitionWith(new HashMap<>(Map.of(
                        "categoryId", string("def-1"),
                        "category", string("Services"))))));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.backfillPimCategoryIds();

        // then
        verify(dynamoDB, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void definitionAlreadyHoldingIdsIsUntouched() {
        // given
        stubTreeWithInternalNodeSharingLeafName();
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "catalogId", string("catalog-1"),
                "categories", new AttributeValue().withL(definitionWith(new HashMap<>(Map.of(
                        "categoryId", string("def-1"),
                        "category", string("Telewizory"),
                        "pimCategoryIds", new AttributeValue().withL(string("170")))))));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.backfillPimCategoryIds();

        // then
        verify(dynamoDB, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void unknownAttributesSurviveTheConversion() {
        // given
        stubTreeWithInternalNodeSharingLeafName();
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "catalogId", string("catalog-1"),
                "categories", new AttributeValue().withL(definitionWith(new HashMap<>(Map.of(
                        "categoryId", string("def-1"),
                        "category", string("Telewizory"),
                        "futureAttribute", string("keep-me"))))));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.backfillPimCategoryIds();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        Map<String, AttributeValue> converted = captor.getValue().getExpressionAttributeValues()
                .get(":categories").getL().getFirst().getM();
        assertEquals("keep-me", converted.get("futureAttribute").getS());
    }

    @Test
    void refreshesThePimCatalogBeforeReadingTheTree() {
        // given
        stubTreeWithInternalNodeSharingLeafName();
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of()));

        // when
        migration.backfillPimCategoryIds();

        // then
        InOrder inOrder = inOrder(pimCatalog);
        inOrder.verify(pimCatalog).refresh();
        inOrder.verify(pimCatalog).allCategories();
    }

    @Test
    void emptyTreeOutsideProdSkipsQuietlyInsteadOfAborting() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());
        when(environment.getProperty("application.env")).thenReturn("localhost");

        // when / then
        assertDoesNotThrow(() -> migration.backfillPimCategoryIds());
        verify(dynamoDB, never()).scan(any(ScanRequest.class));
    }

    @Test
    void emptyTreeOnProdAborts() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());
        when(environment.getProperty("application.env")).thenReturn("prod");

        // when / then
        assertThrows(IllegalStateException.class, () -> migration.backfillPimCategoryIds());
    }
}
