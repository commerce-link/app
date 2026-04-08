package pl.commercelink.templates;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

@Repository
public class EmailTemplatesRepository extends DynamoDbRepository<EmailTemplate> {

    public EmailTemplatesRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public EmailTemplate findByTemplateName(String storeId, String templateName) {
        return dynamoDBMapper.load(EmailTemplate.class, storeId, templateName);
    }

}
