package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierIdentityTest {

    @Test
    void typeOfPlainIdentityIsTheIdentityItself() {
        // when / then
        assertThat(SupplierIdentity.typeOf("Kosatec")).isEqualTo("Kosatec");
    }

    @Test
    void typeOfTokenedIdentityIsThePartBeforeTheFirstDash() {
        // when / then
        assertThat(SupplierIdentity.typeOf("Kosatec-k7f3a9c2")).isEqualTo("Kosatec");
        assertThat(SupplierIdentity.typeOf("manual-k7f3a9c2")).isEqualTo("manual");
    }

    @Test
    void typeOfLegacyManualIdentityIsManualEvenWhenTheLabelContainsDashes() {
        // when / then
        assertThat(SupplierIdentity.typeOf("manual:Asus")).isEqualTo("manual");
        assertThat(SupplierIdentity.typeOf("manual:Hurtownia-A")).isEqualTo("manual");
    }

    @Test
    void isManualRecognisesBothManualShapes() {
        // when / then
        assertThat(SupplierIdentity.isManual("manual:Asus")).isTrue();
        assertThat(SupplierIdentity.isManual("manual-k7f3a9c2")).isTrue();
        assertThat(SupplierIdentity.isManual("Kosatec")).isFalse();
        assertThat(SupplierIdentity.isManual(null)).isFalse();
    }

    @Test
    void hasTokenIsTrueOnlyForDashSeparatedIdentities() {
        // when / then
        assertThat(SupplierIdentity.hasToken("Kosatec-k7f3a9c2")).isTrue();
        assertThat(SupplierIdentity.hasToken("Kosatec")).isFalse();
        assertThat(SupplierIdentity.hasToken("manual:Hurtownia-A")).isFalse();
    }

    @Test
    void newInstanceProducesTypeDashEightLowercaseAlphanumerics() {
        // when
        String identity = SupplierIdentity.newInstance("Kosatec");

        // then
        assertThat(identity).matches("^Kosatec-[a-z0-9]{8}$");
        assertThat(SupplierIdentity.typeOf(identity)).isEqualTo("Kosatec");
    }

    @Test
    void ofRejectsTokensOutsideTheAlphabet() {
        // when / then
        assertThat(SupplierIdentity.of("AcmeB", "seed0001")).isEqualTo("AcmeB-seed0001");
        assertThatThrownBy(() -> SupplierIdentity.of("AcmeB", "SEED-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyLabelStripsOnlyTheLegacyManualPrefix() {
        // when / then
        assertThat(SupplierIdentity.legacyLabel("manual:Asus")).isEqualTo("Asus");
        assertThat(SupplierIdentity.legacyLabel("Kosatec")).isEqualTo("Kosatec");
        assertThat(SupplierIdentity.legacyLabel("Kosatec-k7f3a9c2")).isEqualTo("Kosatec-k7f3a9c2");
    }

    @Test
    void typeNamesMustNotContainTheSeparators() {
        // when / then
        assertThat(SupplierIdentity.isValidTypeName("IngramMicro")).isTrue();
        assertThat(SupplierIdentity.isValidTypeName("CS-Cart")).isFalse();
        assertThat(SupplierIdentity.isValidTypeName("x:y")).isFalse();
        assertThat(SupplierIdentity.isValidTypeName(" ")).isFalse();
    }
}
