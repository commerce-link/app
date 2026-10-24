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
 * Orders by discount code and order day: the code's details page lists and sums the orders placed with it. Sparse, as
 * an order without a code has no discountCode; DynamoDB backfills the orders that already carry one, and until the
 * index is ACTIVE the details page shows the code without its orders. On a new environment V018 may still be building
 * its index on Orders, so the unit waits for the table to be idle first; a timeout fails the start-up and the next
 * start runs the unit again.
 */
@ChangeUnit(id = "V023-add-discount-code-index", order = "023", author = "commercelink")
public class V023_AddDiscountCodeIndex {

    public static final String INDEX = "DiscountCodeIndex";
    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final GlobalSecondaryIndexCreation indexCreation;

    @ChangeUnitConstructor
    public V023_AddDiscountCodeIndex(AmazonDynamoDB dynamoDB) {
        this(dynamoDB, GlobalSecondaryIndexCreation::sleep, TIMEOUT);
    }

    V023_AddDiscountCodeIndex(AmazonDynamoDB dynamoDB, LongConsumer pause, Duration timeout) {
        this.indexCreation = new GlobalSecondaryIndexCreation(dynamoDB, "V023", pause, timeout);
    }

    @Execution
    public void execute() {
        indexCreation.createIfAbsent("Orders", INDEX, "discountCode", "orderedAt", new Projection()
                .withProjectionType(ProjectionType.INCLUDE)
                .withNonKeyAttributes("email", "totalPrice", "discountAmount", "status"));
    }

    @RollbackExecution
    public void rollback() {
        // No-op: after a client-side timeout the index may still be CREATING, and deleting it would lose the build.
    }
}
