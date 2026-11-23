package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.time.Duration;
import java.util.function.LongConsumer;

/**
 * Cancelled orders still awaiting a refund, by store and the day they were cancelled, keys only: the orders list and
 * the Payments page read just those instead of every cancelled order of the last months. Sparse: only an order
 * awaiting a refund carries refundDueSortKey, which the application writes on every save (RefundDueSortKey). No
 * order is given the key here: when the index was introduced no cancelled order held a customer's payment, and an
 * order stored without the key by a version that does not write it enters the index with its next save. Until the
 * index is ACTIVE the repository reads the way it did before (OrdersRepository.findCancelledAwaitingRefund). On a new
 * environment an earlier unit may still be building its index on Orders, so the unit waits for the table to be idle; a
 * timeout fails the start-up and the next start runs the unit again, the step being idempotent.
 */
@ChangeUnit(id = "V029-add-refund-due-index", order = "029", author = "commercelink")
public class V029_AddRefundDueIndex {

    public static final String INDEX = "StoreIdRefundDueIndex";
    static final String SORT_KEY = "refundDueSortKey";
    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final GlobalSecondaryIndexCreation indexCreation;

    @ChangeUnitConstructor
    public V029_AddRefundDueIndex(AmazonDynamoDB dynamoDB) {
        this(dynamoDB, GlobalSecondaryIndexCreation::sleep, TIMEOUT);
    }

    V029_AddRefundDueIndex(AmazonDynamoDB dynamoDB, LongConsumer pause, Duration timeout) {
        this.indexCreation = new GlobalSecondaryIndexCreation(dynamoDB, "V029", pause, timeout);
    }

    @Execution
    public void execute() {
        indexCreation.createIfAbsent("Orders", INDEX, "storeId", SORT_KEY,
                new Projection().withProjectionType(ProjectionType.KEYS_ONLY));
    }

    @RollbackExecution
    public void rollback() {
        // No-op: after a client-side timeout the index may still be CREATING, and deleting it would lose the build.
    }
}
