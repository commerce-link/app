package pl.commercelink.templates;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.orders.notifications.EmailNotificationType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailTemplatesRepositoryTest {

    private final DynamoDBMapper realMapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
    private final DynamoDBMapper dynamoDBMapper = mock(DynamoDBMapper.class);
    private EmailTemplatesRepository repository;

    @BeforeEach
    void setup() {
        repository = new EmailTemplatesRepository(mock(AmazonDynamoDB.class));
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    private EmailTemplate read(String type) {
        return realMapper.marshallIntoObject(EmailTemplate.class, Map.of(
                "storeId", new AttributeValue("default"),
                "templateName", new AttributeValue("RMAItemsRejectedTemplate"),
                "type", new AttributeValue(type),
                "subject", new AttributeValue("Subject"),
                "textBody", new AttributeValue("Body")));
    }

    @Test
    void aRecordWithATypeRemovedFromTheEnumIsReadWithoutAType() {
        // when
        EmailTemplate template = read("RMA_ITEMS_REJECTED");

        // then
        assertThat(template.getType()).isNull();
        assertThat(template.getTemplateName()).isEqualTo("RMAItemsRejectedTemplate");
    }

    @Test
    void aKnownTypeIsReadAsTheEnumAndWrittenUnderTheSameAttribute() {
        // when
        EmailTemplate template = read("ORDER_SHIPPING");

        // then
        assertThat(template.getType()).isEqualTo(EmailNotificationType.ORDER_SHIPPING);
        assertThat(realMapper.getTableModel(EmailTemplate.class).convert(template))
                .containsEntry("type", new AttributeValue("ORDER_SHIPPING"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingAStoreSkipsTemplatesOfUnknownType() {
        // given
        EmailTemplate known = read("ORDER_SHIPPING");
        EmailTemplate unknown = read("RMA_ITEMS_REJECTED");
        PaginatedQueryList<EmailTemplate> page = mock(PaginatedQueryList.class);
        when(page.stream()).thenReturn(List.of(known, unknown).stream());
        when(dynamoDBMapper.query(eq(EmailTemplate.class), any(DynamoDBQueryExpression.class))).thenReturn(page);

        // when
        List<EmailTemplate> templates = repository.findAllOfStore("default");

        // then
        assertThat(templates).containsExactly(known);
    }
}
