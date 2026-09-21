package pl.commercelink.templates;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.List;

@Slf4j
@Repository
public class EmailTemplatesRepository extends DynamoDbRepository<EmailTemplate> {

    /** The store whose templates every other store falls back to when it has no own copy. */
    public static final String DEFAULT_STORE = "default";

    public EmailTemplatesRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public EmailTemplate findByTemplateName(String storeId, String templateName) {
        return dynamoDBMapper.load(EmailTemplate.class, storeId, templateName);
    }

    /**
     * Every template of one store in a single query (the templates page lists all notification types).
     * Records whose type is no longer a notification type are skipped: nothing sends them.
     */
    public List<EmailTemplate> findAllOfStore(String storeId) {
        EmailTemplate key = new EmailTemplate();
        key.setStoreId(storeId);
        return dynamoDBMapper.query(EmailTemplate.class, new DynamoDBQueryExpression<EmailTemplate>().withHashKeyValues(key))
                .stream()
                .filter(template -> {
                    if (template.getType() != null) {
                        return true;
                    }
                    log.warn("Skipping email template {}/{} of unknown type {}", storeId, template.getTemplateName(), template.getTypeName());
                    return false;
                })
                .toList();
    }
}
