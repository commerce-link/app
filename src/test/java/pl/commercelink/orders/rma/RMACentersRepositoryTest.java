package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RMACentersRepositoryTest {

    @Test
    void providerScanMatchesDefaultCentersByTypeToo() {
        // given
        String tokenedIdentity = "Elko-k7f3a9c2";

        // when
        DynamoDBScanExpression scan = RMACentersRepository.providerScan("store-1", tokenedIdentity);

        // then
        assertThat(scan.getFilterExpression()).isEqualTo(
                "(storeId = :storeId and provider = :provider)"
                        + " or (storeId = :storeIdDefault and (provider = :provider or provider = :type))");
        assertThat(scan.getExpressionAttributeValues().get(":provider").getS()).isEqualTo("Elko-k7f3a9c2");
        assertThat(scan.getExpressionAttributeValues().get(":type").getS()).isEqualTo("Elko");
        assertThat(scan.getExpressionAttributeValues().get(":storeIdDefault").getS()).isEqualTo("default");
    }

    @Test
    void providerScanKeepsExactMatchForLegacyIdentities() {
        // when
        DynamoDBScanExpression scan = RMACentersRepository.providerScan("store-1", "Elko");

        // then
        assertThat(scan.getExpressionAttributeValues().get(":provider").getS()).isEqualTo("Elko");
        assertThat(scan.getExpressionAttributeValues().get(":type").getS()).isEqualTo("Elko");
    }

    @Test
    void providerScanDoesNotMatchDefaultCentersForManualIdentities() {
        // when
        DynamoDBScanExpression scan = RMACentersRepository.providerScan("store-1", "manual-k7f3a9c2");

        // then
        // default centres are keyed by external supplier type, never "manual", so this matches nothing
        assertThat(scan.getExpressionAttributeValues().get(":type").getS()).isEqualTo("manual");
    }
}
