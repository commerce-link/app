package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.pim.api.PimCategories;
import pl.commercelink.pim.api.PimCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.scanAndProcess;

@ChangeUnit(id = "V010-backfill-category-definition-pim-category-ids", order = "010", author = "commercelink")
@RequiredArgsConstructor
public class V010_BackfillCategoryDefinitionPimCategoryIds {

    private static final String TABLE_NAME = "Catalogs";
    private static final String LANG = "pl";

    private final AmazonDynamoDB dynamoDB;
    private final PimCatalog pimCatalog;
    private final Environment environment;

    @Execution
    public void backfillPimCategoryIds() {
        pimCatalog.refresh();
        Map<String, String> leafIdsByName = leafIdsByName();
        if (leafIdsByName.isEmpty()) {
            if (shouldAbort(pimAdapterPresent(), environment.getProperty("application.env"))) {
                throw new IllegalStateException("PIM category tree unavailable - aborting V010");
            }
            System.err.println("V010: PIM category tree unavailable - skipping legacy category conversion");
            return;
        }
        scanAndProcess(dynamoDB, TABLE_NAME, List.of("storeId", "catalogId", "categories"), item -> {
            AttributeValue categories = item.get("categories");
            if (categories == null || categories.getL() == null) {
                return;
            }
            List<AttributeValue> converted = categories.getL().stream()
                    .map(definition -> convert(definition, leafIdsByName))
                    .toList();
            if (converted.equals(categories.getL())) {
                return;
            }
            executeUpdate(dynamoDB, TABLE_NAME,
                    Map.of("storeId", item.get("storeId"), "catalogId", item.get("catalogId")),
                    "SET categories = :categories", null,
                    Map.of(":categories", new AttributeValue().withL(converted)));
        });
    }

    static AttributeValue convert(AttributeValue definition, Map<String, String> leafIdsByName) {
        Map<String, AttributeValue> attributes = definition.getM();
        if (attributes == null) {
            return definition;
        }
        AttributeValue existingIds = attributes.get("pimCategoryIds");
        if (existingIds != null && existingIds.getL() != null && !existingIds.getL().isEmpty()) {
            return definition;
        }
        AttributeValue legacy = attributes.get("category");
        if (legacy == null || legacy.getS() == null) {
            return definition;
        }
        String leafId = leafIdsByName.get(legacy.getS());
        if (leafId == null) {
            System.err.println("V010: unresolvable legacy category '" + legacy.getS() + "' left without mapping");
            return definition;
        }
        Map<String, AttributeValue> converted = new HashMap<>(attributes);
        converted.put("pimCategoryIds", new AttributeValue().withL(new AttributeValue().withS(leafId)));
        return new AttributeValue().withM(converted);
    }

    static boolean shouldAbort(boolean pimAdapterPresent, String applicationEnv) {
        return pimAdapterPresent && "prod".equals(applicationEnv);
    }

    private static boolean pimAdapterPresent() {
        return ServiceLoader.load(PimCatalogDescriptor.class).findFirst().isPresent();
    }

    private Map<String, String> leafIdsByName() {
        List<PimCategory> categories = pimCatalog.allCategories().stream()
                .filter(category -> LANG.equals(category.lang()))
                .toList();
        PimCategories tree = new PimCategories(categories);
        Map<String, String> leafIdsByName = new HashMap<>();
        categories.stream()
                .filter(category -> tree.childrenOf(category.id()).isEmpty())
                .forEach(leaf -> leafIdsByName.putIfAbsent(leaf.name(), leaf.id()));
        return leafIdsByName;
    }

    @RollbackExecution
    public void rollback() {
    }
}
