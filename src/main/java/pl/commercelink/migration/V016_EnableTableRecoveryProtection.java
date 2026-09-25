package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.DescribeContinuousBackupsRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.PointInTimeRecoveryDescription;
import com.amazonaws.services.dynamodbv2.model.PointInTimeRecoverySpecification;
import com.amazonaws.services.dynamodbv2.model.PointInTimeRecoveryStatus;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.UpdateContinuousBackupsRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import pl.commercelink.notifications.StoreNotificationRecord;
import pl.commercelink.scheduling.DailyScheduleExecutionCount;

import java.util.List;

/**
 * Keeps point-in-time recovery and deletion protection on every application table. Runs on every start so a table
 * added by a later migration, or one whose settings were switched off by hand, is covered again.
 *
 * A failure is logged and does not stop the start: the role needs dynamodb:UpdateContinuousBackups and
 * dynamodb:DescribeContinuousBackups, and an environment whose IAM policy predates them must keep booting.
 * Excluded from localdev: its in-memory DynamoDB Local has nothing to recover, and deletion protection there would
 * only get in the way of dropping tables by hand.
 */
@Slf4j
@Profile("!localdev")
@RequiredArgsConstructor
@ChangeUnit(id = "V016-enable-table-recovery-protection", order = "016", author = "commercelink", runAlways = true)
public class V016_EnableTableRecoveryProtection {

    static final List<String> TABLES = List.of(
            "AppMigrationsHistory",
            "Baskets",
            "Catalogs",
            "ClientVerifications",
            DailyScheduleExecutionCount.TABLE_NAME,
            "Deliveries",
            "EmailTemplates",
            "OrderEvents",
            "OrderFilters",
            "OrderItems",
            "Orders",
            "Products",
            "RMA",
            "RMACenters",
            "RMAItems",
            "ShipmentTrackings",
            StoreNotificationRecord.TABLE_NAME,
            "Stores",
            "TaxonomyCategoryMappings",
            "WarehouseDocumentItems",
            "WarehouseDocumentSequences",
            "WarehouseDocuments",
            "WarehouseItems");

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void protectTables() {
        TABLES.forEach(this::protect);
    }

    private void protect(String table) {
        try {
            enablePointInTimeRecovery(table);
            enableDeletionProtection(table);
        } catch (ResourceNotFoundException e) {
            log.warn("Skipping recovery protection of missing DynamoDB table {}", table);
        } catch (AmazonDynamoDBException e) {
            log.error("Could not protect DynamoDB table {}: {} ({})", table, e.getErrorMessage(), e.getErrorCode());
        }
    }

    private void enablePointInTimeRecovery(String table) {
        PointInTimeRecoveryDescription pitr = dynamoDB.describeContinuousBackups(
                        new DescribeContinuousBackupsRequest().withTableName(table))
                .getContinuousBackupsDescription()
                .getPointInTimeRecoveryDescription();
        if (pitr != null && PointInTimeRecoveryStatus.ENABLED.toString().equals(pitr.getPointInTimeRecoveryStatus())) {
            return;
        }
        dynamoDB.updateContinuousBackups(new UpdateContinuousBackupsRequest()
                .withTableName(table)
                .withPointInTimeRecoverySpecification(new PointInTimeRecoverySpecification()
                        .withPointInTimeRecoveryEnabled(true)));
        log.info("Enabled point-in-time recovery on DynamoDB table {}", table);
    }

    private void enableDeletionProtection(String table) {
        Boolean enabled = dynamoDB.describeTable(new DescribeTableRequest().withTableName(table))
                .getTable()
                .getDeletionProtectionEnabled();
        if (Boolean.TRUE.equals(enabled)) {
            return;
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName(table)
                .withDeletionProtectionEnabled(true));
        log.info("Enabled deletion protection on DynamoDB table {}", table);
    }

    @RollbackExecution
    public void rollback() {}
}
