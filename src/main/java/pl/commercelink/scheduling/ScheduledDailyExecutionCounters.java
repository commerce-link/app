package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    private LocalDate executionDate;

    public static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }
}
