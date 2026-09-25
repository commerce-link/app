package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.ContinuousBackupsDescription;
import com.amazonaws.services.dynamodbv2.model.DescribeContinuousBackupsRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeContinuousBackupsResult;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.PointInTimeRecoveryDescription;
import com.amazonaws.services.dynamodbv2.model.PointInTimeRecoveryStatus;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.UpdateContinuousBackupsRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class V016_EnableTableRecoveryProtectionTest {

    @Mock
    AmazonDynamoDB dynamoDB;

    @Test
    void enablesRecoveryAndDeletionProtectionOnUnprotectedTables() {
        pitrStatus(PointInTimeRecoveryStatus.DISABLED);
        deletionProtection(false);

        new V016_EnableTableRecoveryProtection(dynamoDB).protectTables();

        ArgumentCaptor<UpdateContinuousBackupsRequest> backups = ArgumentCaptor.forClass(UpdateContinuousBackupsRequest.class);
        verify(dynamoDB, times(V016_EnableTableRecoveryProtection.TABLES.size())).updateContinuousBackups(backups.capture());
        assertThat(backups.getAllValues())
                .extracting(UpdateContinuousBackupsRequest::getTableName)
                .containsExactlyElementsOf(V016_EnableTableRecoveryProtection.TABLES);
        assertThat(backups.getAllValues())
                .allMatch(request -> request.getPointInTimeRecoverySpecification().getPointInTimeRecoveryEnabled());

        ArgumentCaptor<UpdateTableRequest> tables = ArgumentCaptor.forClass(UpdateTableRequest.class);
        verify(dynamoDB, times(V016_EnableTableRecoveryProtection.TABLES.size())).updateTable(tables.capture());
        assertThat(tables.getAllValues()).allMatch(UpdateTableRequest::getDeletionProtectionEnabled);
    }

    @Test
    void leavesAlreadyProtectedTablesUntouched() {
        pitrStatus(PointInTimeRecoveryStatus.ENABLED);
        deletionProtection(true);

        new V016_EnableTableRecoveryProtection(dynamoDB).protectTables();

        verify(dynamoDB, never()).updateContinuousBackups(any());
        verify(dynamoDB, never()).updateTable(any(UpdateTableRequest.class));
    }

    @Test
    void aFailingTableDoesNotStopTheOthers() {
        pitrStatus(PointInTimeRecoveryStatus.DISABLED);
        deletionProtection(false);
        lenient().when(dynamoDB.describeContinuousBackups(argThat((DescribeContinuousBackupsRequest r) ->
                        r != null && "Orders".equals(r.getTableName()))))
                .thenThrow(accessDenied());
        lenient().when(dynamoDB.describeContinuousBackups(argThat((DescribeContinuousBackupsRequest r) ->
                        r != null && "Stores".equals(r.getTableName()))))
                .thenThrow(new ResourceNotFoundException("missing"));

        new V016_EnableTableRecoveryProtection(dynamoDB).protectTables();

        int protectedTables = V016_EnableTableRecoveryProtection.TABLES.size() - 2;
        verify(dynamoDB, times(protectedTables)).updateContinuousBackups(any());
        verify(dynamoDB, times(protectedTables)).updateTable(any(UpdateTableRequest.class));
    }

    private void pitrStatus(PointInTimeRecoveryStatus status) {
        lenient().when(dynamoDB.describeContinuousBackups(any())).thenReturn(new DescribeContinuousBackupsResult()
                .withContinuousBackupsDescription(new ContinuousBackupsDescription()
                        .withPointInTimeRecoveryDescription(new PointInTimeRecoveryDescription()
                                .withPointInTimeRecoveryStatus(status))));
    }

    private void deletionProtection(boolean enabled) {
        lenient().when(dynamoDB.describeTable(any(DescribeTableRequest.class))).thenReturn(new DescribeTableResult()
                .withTable(new TableDescription().withDeletionProtectionEnabled(enabled)));
    }

    private static AmazonDynamoDBException accessDenied() {
        AmazonDynamoDBException e = new AmazonDynamoDBException("not authorized");
        e.setErrorCode("AccessDeniedException");
        return e;
    }
}
