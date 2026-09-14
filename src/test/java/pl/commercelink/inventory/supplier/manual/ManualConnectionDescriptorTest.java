package pl.commercelink.inventory.supplier.manual;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.ParsedRow;

import static org.assertj.core.api.Assertions.assertThat;

class ManualConnectionDescriptorTest {

    @Test
    void stampsRowsWithTheIdentityForBothManualShapes() {
        // given / when / then
        for (String identity : new String[]{"manual:Asus", "manual-k7f3a9c2"}) {
            ManualConnectionDescriptor descriptor = new ManualConnectionDescriptor(identity);
            assertThat(descriptor.supplierInfo().name()).isEqualTo(identity);
            FeedFormat.Csv csv = (FeedFormat.Csv) descriptor.feedFormat();
            ParsedRow row = csv.parser().tryParse(new String[]{
                    "4711111111111", "MFN-1", "Brand", "Name", "Cat", "10.0", "PLN", "3", "2"}).orElseThrow();
            assertThat(row.item().supplier()).isEqualTo(identity);
        }
    }

    @Test
    void createsAProviderThatNeverDownloads() throws Exception {
        // given / when / then
        assertThat(new ManualConnectionDescriptor("manual-k7f3a9c2").create(java.util.Map.of()).download()).isEmpty();
    }
}
