package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;

import java.time.LocalDate;

@DynamoDBDocument
public class OrderReview {
    @DynamoDBAttribute(attributeName = "referenceNo")
    private String referenceNo;
    @DynamoDBAttribute(attributeName = "requestedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requestedAt;
    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private OrderReviewStatus status;

    public OrderReview() {
    }

    public OrderReview(OrderReviewStatus status) {
        this.status = status;
    }

    public boolean hasOneOfStatuses(OrderReviewStatus... statuses) {
        for (OrderReviewStatus status : statuses) {
            if (this.status == status) {
                return true;
            }
        }
        return false;
    }

    // required by dynamodb
    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public OrderReviewStatus getStatus() {
        return status;
    }

    public void setStatus(OrderReviewStatus status) {
        this.status = status;
    }

    public LocalDate getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDate requestedAt) {
        this.requestedAt = requestedAt;
    }
}
