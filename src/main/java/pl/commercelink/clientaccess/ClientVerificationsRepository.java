package pl.commercelink.clientaccess;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ClientVerificationsRepository extends DynamoDbRepository<ClientVerification> {

    public ClientVerificationsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public List<ClientVerification> findBySubjectKey(String subjectKey) {
        ClientVerification hashKey = new ClientVerification();
        hashKey.setSubjectKey(subjectKey);

        DynamoDBQueryExpression<ClientVerification> queryExpression = new DynamoDBQueryExpression<ClientVerification>()
                .withHashKeyValues(hashKey)
                .withConsistentRead(true);

        return new ArrayList<>(dynamoDBMapper.query(ClientVerification.class, queryExpression));
    }

    public Optional<ClientVerification> findById(String subjectKey, String verificationId) {
        return Optional.ofNullable(dynamoDBMapper.load(ClientVerification.class, subjectKey, verificationId));
    }
}
