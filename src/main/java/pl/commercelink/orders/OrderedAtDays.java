package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.time.LocalDate;
import java.util.Map;

/**
 * Days of orderedAt, both inclusive and both optional, as the range part of an index key condition. The days are
 * compared as text: "from" as the bare date sorts before every time of that day (and equals a legacy date-only value),
 * "to" ends before the next day, so the last fraction of a second of the "to" day is still in.
 */
record OrderedAtDays(LocalDate from, LocalDate to) {

    String keyCondition(String partitionCondition, Map<String, AttributeValue> values, Map<String, String> names) {
        if (from == null && to == null) {
            // DynamoDB rejects a name placeholder that no expression uses
            return partitionCondition;
        }
        names.put("#orderedAt", "orderedAt");
        if (from != null) {
            values.put(":from", new AttributeValue().withS(from.toString()));
        }
        if (from != null && to != null) {
            // a key condition takes one comparison of the range key, so with both days it is BETWEEN (inclusive): the
            // upper bound is the highest text of the "to" day, above every "yyyy-MM-ddT…" of it and below the next day
            values.put(":toEnd", new AttributeValue().withS(to + "T\uffff"));
            return partitionCondition + " AND #orderedAt BETWEEN :from AND :toEnd";
        }
        if (from != null) {
            return partitionCondition + " AND #orderedAt >= :from";
        }
        values.put(":toNext", new AttributeValue().withS(to.plusDays(1).toString()));
        return partitionCondition + " AND #orderedAt < :toNext";
    }
}
