package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategories;
import pl.commercelink.pim.api.PimCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.scanAndProcess;

@ChangeUnit(id = "V010-migrate-category-definition-categories", order = "010", author = "commercelink")
@RequiredArgsConstructor
public class V010_MigrateCategoryDefinitionCategories {

    private static final String TABLE_NAME = "Catalogs";
    private static final String LANG = "pl";

    private static final String PROD_ENV = "prod";

    private final AmazonDynamoDB dynamoDB;
    private final PimCatalog pimCatalog;
    private final Environment environment;

    @Execution
    public void migrateCategories() {
        pimCatalog.refresh();
        Map<String, String> idByLeafName = idByLeafName();
        if (idByLeafName.isEmpty()) {
            if (PROD_ENV.equals(environment.getProperty("application.env"))) {
                throw new IllegalStateException(
                        "Aborting V010: PIM served no leaf categories, converting now would wipe every category mapping");
            }
            System.out.println("V010 skipped: PIM served no leaf categories and application.env is not prod");
            return;
        }

        scanAndProcess(dynamoDB, TABLE_NAME, List.of("storeId", "catalogId", "categories"), item -> {
            List<AttributeValue> migrated = migratedDefinitions(item.get("categories"), idByLeafName);
            if (migrated.isEmpty()) {
                return;
            }
            executeUpdate(dynamoDB, TABLE_NAME,
                    Map.of("storeId", item.get("storeId"), "catalogId", item.get("catalogId")),
                    "SET #categories = :categories",
                    Map.of("#categories", "categories"),
                    Map.of(":categories", new AttributeValue().withL(migrated)));
        });
    }

    static List<AttributeValue> migratedDefinitions(AttributeValue categoriesAttribute,
                                                    Map<String, String> idByLeafName) {
        if (idByLeafName.isEmpty()) {
            throw new IllegalStateException("Cannot migrate with no leaf categories available");
        }
        if (categoriesAttribute == null || categoriesAttribute.getL() == null) {
            return List.of();
        }

        List<AttributeValue> migrated = new ArrayList<>();
        for (AttributeValue definition : categoriesAttribute.getL()) {
            migrated.add(definition.getM() == null
                    ? definition
                    : new AttributeValue().withM(migratedDefinition(definition.getM(), idByLeafName)));
        }
        return migrated;
    }

    private static Map<String, AttributeValue> migratedDefinition(Map<String, AttributeValue> definition,
                                                                 Map<String, String> idByLeafName) {
        Map<String, AttributeValue> result = new HashMap<>(definition);
        if (hasCategories(definition)) {
            return result;
        }

        List<AttributeValue> ids = new ArrayList<>();
        AttributeValue legacy = definition.get("category");
        if (legacy != null && legacy.getS() != null && !legacy.getS().isBlank()) {
            String id = idByLeafName.get(legacy.getS().trim());
            if (id == null) {
                System.out.println("V010: no leaf category named \"" + legacy.getS()
                        + "\", leaving categories empty");
            } else {
                ids.add(new AttributeValue().withS(id));
            }
        }
        result.put("categories", new AttributeValue().withL(ids));
        return result;
    }

    private static boolean hasCategories(Map<String, AttributeValue> definition) {
        AttributeValue categories = definition.get("categories");
        return categories != null && categories.getL() != null && !categories.getL().isEmpty();
    }

    private Map<String, String> idByLeafName() {
        PimCategories categories = new PimCategories(pimCatalog.allCategories().stream()
                .filter(category -> LANG.equals(category.lang()))
                .toList());

        Map<String, String> idByName = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (PimCategory topLevel : categories.topLevels()) {
            for (PimCategory leaf : categories.leavesUnder(topLevel.id())) {
                if (leaf.name() == null || leaf.name().isBlank() || leaf.id() == null) {
                    continue;
                }
                String name = leaf.name().trim();
                seen.merge(name, 1, Integer::sum);
                idByName.putIfAbsent(name, leaf.id());
            }
        }
        seen.forEach((name, count) -> {
            if (count > 1) {
                System.out.println("V010: leaf category name \"" + name + "\" is ambiguous (" + count
                        + " leaves), using " + idByName.get(name));
            }
        });
        return idByName;
    }

    @RollbackExecution
    public void rollback() {}
}
