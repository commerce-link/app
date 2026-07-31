package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V010_MigrateCategoryDefinitionCategoriesTest {

    private static final Map<String, String> ID_BY_LEAF_NAME = Map.of(
            "Klawiatury", "194",
            "Myszki", "195",
            "Telewizory", "1584");

    @Mock
    private AmazonDynamoDB dynamoDB;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private Environment environment;
    @InjectMocks
    private V010_MigrateCategoryDefinitionCategories migration;

    private static AttributeValue definition(String category, List<String> categories) {
        Map<String, AttributeValue> map = new java.util.HashMap<>();
        map.put("categoryId", new AttributeValue().withS("def-1"));
        if (category != null) {
            map.put("category", new AttributeValue().withS(category));
        }
        if (categories != null) {
            map.put("categories", new AttributeValue().withL(
                    categories.stream().map(id -> new AttributeValue().withS(id)).toList()));
        }
        return new AttributeValue().withM(map);
    }

    private static AttributeValue list(AttributeValue... definitions) {
        return new AttributeValue().withL(List.of(definitions));
    }

    private static List<String> categoriesOf(AttributeValue definition) {
        AttributeValue categories = definition.getM().get("categories");
        return categories == null || categories.getL() == null
                ? null
                : categories.getL().stream().map(AttributeValue::getS).toList();
    }

    @Test
    void convertsLegacyNameToLeafId() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("194");
    }

    @Test
    void keepsLegacyNameAttributeIntact() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(migrated.get(0).getM().get("category").getS()).isEqualTo("Klawiatury");
    }

    @Test
    void leavesCategoriesEmptyForNameOutsideTheLeafTree() {
        // given
        AttributeValue categories = list(definition("Services", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).isEmpty();
        assertThat(migrated.get(0).getM().get("category").getS()).isEqualTo("Services");
    }

    @Test
    void resolvesNameThatAlsoExistsAsInternalNodeToTheLeaf() {
        // given
        AttributeValue categories = list(definition("Telewizory", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("1584");
    }

    @Test
    void doesNotTouchDefinitionThatAlreadyHasCategories() {
        // given
        AttributeValue categories = list(definition("Klawiatury", List.of("999")));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("999");
    }

    @Test
    void leavesCategoriesEmptyWhenLegacyNameIsAbsent() {
        // given
        AttributeValue categories = list(definition(null, null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).isEmpty();
    }

    @Test
    void convertsEveryDefinitionInTheCatalog() {
        // given
        AttributeValue categories = list(
                definition("Klawiatury", null),
                definition("Myszki", null),
                definition("Services", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("194");
        assertThat(categoriesOf(migrated.get(1))).containsExactly("195");
        assertThat(categoriesOf(migrated.get(2))).isEmpty();
    }

    @Test
    void preservesUnrelatedDefinitionAttributes() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(migrated.get(0).getM().get("categoryId").getS()).isEqualTo("def-1");
    }

    @Test
    void returnsEmptyListForMissingCategoriesAttribute() {
        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(null, ID_BY_LEAF_NAME);

        // then
        assertThat(migrated).isEmpty();
    }

    @Test
    void rejectsAnEmptyLeafMap() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when / then
        assertThatThrownBy(() -> V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no leaf categories");
    }

    @Test
    void convertsAndWritesTheUpdatedDefinitionBackToTheTable() {
        // given
        PimCategory topLevel = new PimCategory("100", null, "Elektronika", "pl");
        PimCategory leaf = new PimCategory("194", "100", "Klawiatury", "pl");
        when(pimCatalog.allCategories()).thenReturn(List.of(topLevel, leaf));
        Map<String, AttributeValue> item = Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "catalogId", new AttributeValue().withS("catalog-1"),
                "categories", list(definition("Klawiatury", null)));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.migrateCategories();

        // then
        verify(pimCatalog).refresh();
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();
        assertThat(request.getTableName()).isEqualTo("Catalogs");
        assertThat(request.getKey().get("storeId").getS()).isEqualTo("store-1");
        assertThat(request.getKey().get("catalogId").getS()).isEqualTo("catalog-1");
        assertThat(request.getUpdateExpression()).isEqualTo("SET #categories = :categories");
        assertThat(request.getExpressionAttributeNames()).containsEntry("#categories", "categories");
        AttributeValue writtenDefinition = request.getExpressionAttributeValues()
                .get(":categories").getL().get(0);
        assertThat(categoriesOf(writtenDefinition)).containsExactly("194");
        assertThat(writtenDefinition.getM().get("category").getS()).isEqualTo("Klawiatury");
    }

    @Test
    void nonProdWithEmptyTreeLogsAndReturnsWithoutWriting() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());
        when(environment.getProperty("application.env")).thenReturn("local");

        // when
        migration.migrateCategories();

        // then
        verify(dynamoDB, never()).scan(any());
        verify(dynamoDB, never()).updateItem(any());
    }

    @Test
    void prodWithEmptyTreeThrows() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());
        when(environment.getProperty("application.env")).thenReturn("prod");

        // when / then
        assertThatThrownBy(() -> migration.migrateCategories())
                .isInstanceOf(IllegalStateException.class);
        verify(dynamoDB, never()).scan(any());
        verify(dynamoDB, never()).updateItem(any());
    }

    @Test
    void nothingToConvertMeansNoWrite() {
        // given
        AttributeValue firstBuild = definition("Klawiatury", List.of("999"));
        AttributeValue secondBuild = definition("Klawiatury", List.of("999"));
        assertThat(firstBuild).isNotSameAs(secondBuild).isEqualTo(secondBuild);

        PimCategory topLevel = new PimCategory("100", null, "Elektronika", "pl");
        PimCategory leaf = new PimCategory("194", "100", "Klawiatury", "pl");
        when(pimCatalog.allCategories()).thenReturn(List.of(topLevel, leaf));
        Map<String, AttributeValue> item = Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "catalogId", new AttributeValue().withS("catalog-1"),
                "categories", list(definition("Klawiatury", List.of("999"))));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.migrateCategories();

        // then
        verify(dynamoDB, never()).updateItem(any());
    }

    @Test
    void writesWhenAnUnresolvableNameLeavesCategoriesNewlyEmpty() {
        // given
        PimCategory topLevel = new PimCategory("100", null, "Elektronika", "pl");
        PimCategory leaf = new PimCategory("194", "100", "Klawiatury", "pl");
        when(pimCatalog.allCategories()).thenReturn(List.of(topLevel, leaf));
        Map<String, AttributeValue> item = Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "catalogId", new AttributeValue().withS("catalog-1"),
                "categories", list(definition("Services", null)));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.migrateCategories();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        AttributeValue writtenDefinition = captor.getValue().getExpressionAttributeValues()
                .get(":categories").getL().get(0);
        assertThat(categoriesOf(writtenDefinition)).isEmpty();
        assertThat(writtenDefinition.getM().get("category").getS()).isEqualTo("Services");
    }

    @Test
    void resolvesCollidingNameToTheLeafWhenBuildingTheMapFromTheRealTree() {
        // given
        PimCategory root = new PimCategory("1", null, "Elektronika", "pl");
        PimCategory internalNode = new PimCategory("8760", "1", "Telewizory", "pl");
        PimCategory internalChild = new PimCategory("8761", "8760", "Telewizory plazmowe", "pl");
        PimCategory leaf = new PimCategory("1584", "1", "Telewizory", "pl");
        when(pimCatalog.allCategories()).thenReturn(List.of(root, internalNode, internalChild, leaf));
        Map<String, AttributeValue> item = Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "catalogId", new AttributeValue().withS("catalog-1"),
                "categories", list(definition("Telewizory", null)));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(item)));

        // when
        migration.migrateCategories();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        AttributeValue writtenDefinition = captor.getValue().getExpressionAttributeValues()
                .get(":categories").getL().get(0);
        assertThat(categoriesOf(writtenDefinition)).containsExactly("1584").doesNotContain("8760");
    }
}
