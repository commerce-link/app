package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@DynamoDBTable(tableName = ScheduledDailyExecutionCounters.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class ScheduledDailyExecutionCounters {

    public static final String TABLE_NAME = "ScheduledDailyExecutionCounters";
    public static final String STORE_ID_ATTRIBUTE = "storeId";
    public static final String DATE_ATTRIBUTE = "executionDate";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @DynamoDBHashKey(attributeName = STORE_ID_ATTRIBUTE)
    private String storeId;
    @DynamoDBRangeKey(attributeName = DATE_ATTRIBUTE)
    private String executionDate;
    @DynamoDBAttribute(attributeName = "ordersImport")
    private Map<String, Long> ordersImport = new HashMap<>();
    @DynamoDBAttribute(attributeName = "returnsImport")
    private Map<String, Long> returnsImport = new HashMap<>();
    @DynamoDBAttribute(attributeName = "supplierFeed")
    private Map<String, Long> supplierFeed = new HashMap<>();
    @DynamoDBAttribute(attributeName = "pricelist")
    private Map<String, Long> pricelist = new HashMap<>();

    public ScheduledDailyExecutionCounters(String storeId, LocalDate executionDate) {
        this.storeId = storeId;
        this.executionDate = formatDate(executionDate);
    }

    @DynamoDBIgnore
    public Map<String, Long> countersOf(ScheduledExecution scheduledExecution) {
        return switch (scheduledExecution) {
            case ORDERS_IMPORT -> ordersImport;
            case RETURNS_IMPORT -> returnsImport;
            case SUPPLIER_FEED -> supplierFeed;
            case PRICELIST -> pricelist;
        };
    }

    @DynamoDBIgnore
    public long total(ScheduledExecution scheduledExecution) {
        return countersOf(scheduledExecution).values().stream().mapToLong(Long::longValue).sum();
    }

    public static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }
}
