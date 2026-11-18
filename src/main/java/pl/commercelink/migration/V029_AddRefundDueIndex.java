package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.time.Duration;
import java.time.LocalDate;
import java.util.function.LongConsumer;

/**
 * Cancelled orders still awaiting a refund, by store and order day, keys only: the orders list and the Payments page
 * read just those instead of every cancelled order of the last months. Sparse: only an order awaiting a refund carries
 * refundDueSortKey, which the application writes on every save (RefundDueSortKey). Orders cancelled before this unit
 * get the key from the backfill, which runs first so that DynamoDB builds the index over keyed items; until the index
 * is ACTIVE the repository reads the way it did before (OrdersRepository.findCancelledAwaitingRefund). On a new
 * environment an earlier unit may still be building its index on Orders, so the unit waits for the table to be idle; a
 * timeout fails the start-up and the next start runs the unit again, both steps being idempotent.
 */
@ChangeUnit(id = "V029-add-refund-due-index", order = "029", author = "commercelink")
public class V029_AddRefundDueIndex {

    public static final String INDEX = "StoreIdRefundDueIndex";
    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final RefundDueKeyBackfill backfill;
    private final GlobalSecondaryIndexCreation indexCreation;

    @ChangeUnitConstructor
    public V029_AddRefundDueIndex(AmazonDynamoDB dynamoDB) {
        this(dynamoDB, GlobalSecondaryIndexCreation::sleep, TIMEOUT);
    }

    V029_AddRefundDueIndex(AmazonDynamoDB dynamoDB, LongConsumer pause, Duration timeout) {
        this.backfill = new RefundDueKeyBackfill(dynamoDB);
        this.indexCreation = new GlobalSecondaryIndexCreation(dynamoDB, "V029", pause, timeout);
    }

    @Execution
    public void execute() {
        backfill.run(LocalDate.now());
        indexCreation.createIfAbsent("Orders", INDEX, "storeId", RefundDueKeyBackfill.ATTRIBUTE,
                new Projection().withProjectionType(ProjectionType.KEYS_ONLY));
    }

    @RollbackExecution
    public void rollback() {
        // No-op: after a client-side timeout the index may still be CREATING, and deleting it would lose the build.
    }
}
