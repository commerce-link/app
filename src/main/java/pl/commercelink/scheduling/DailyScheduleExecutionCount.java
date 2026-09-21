package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;

import java.time.LocalDate;
import java.time.YearMonth;

@DynamoDBTable(tableName = DailyScheduleExecutionCount.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class DailyScheduleExecutionCount {

    public static final String TABLE_NAME = "DailyScheduleExecutionCounts";
    public static final String STORE_ID = "storeId";
    public static final String COUNTER_KEY = "counterKey";
    public static final String EXECUTION_DATE = "executionDate";
    public static final String EXECUTION_TYPE = "executionType";
    public static final String TARGET = "target";
    public static final String EXECUTION_COUNT = "executionCount";

    private static final String SEPARATOR = "#";

    @DynamoDBHashKey(attributeName = STORE_ID)
    private String storeId;

    @DynamoDBRangeKey(attributeName = COUNTER_KEY)
    private String counterKey;

    @DynamoDBAttribute(attributeName = EXECUTION_DATE)
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    private LocalDate executionDate;

    @DynamoDBAttribute(attributeName = EXECUTION_TYPE)
    @DynamoDBTypeConvertedEnum
    private ScheduledExecution executionType;

    @DynamoDBAttribute(attributeName = TARGET)
    private String target;

    @DynamoDBAttribute(attributeName = EXECUTION_COUNT)
    private long executionCount;

    public static String counterKey(LocalDate date, ScheduledExecution type, String target) {
        return dayPrefix(date) + type.name() + SEPARATOR + target;
    }

    public static String dayPrefix(LocalDate date) {
        return date + SEPARATOR;
    }

    public static String monthPrefix(YearMonth month) {
        return month + "-";
    }
}
