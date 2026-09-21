package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.LogoImageType;
import pl.commercelink.stores.Store;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandingFormTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    private BrandingForm validForm() {
        BrandingForm form = new BrandingForm();
        form.setStoreName("Demo Store");
        form.setPrimaryColor("#1b4db1");
        return form;
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("logoFile", name, "application/octet-stream", content);
    }

    @Test
    void acceptsANameWithoutLogoAndWithoutColour() {
        // given
        BrandingForm form = validForm();
        form.setPrimaryColor("  ");

        // when / then
        assertThat(form.validate()).isEmpty();
        assertThat(form.logoUpload()).isEmpty();
    }

    @Test
    void reportsErrorsInTheOrderTheFieldsAppearOnThePage() {
        // given
        BrandingForm form = new BrandingForm();
        form.setStoreName(" ");
        form.setLogoFile(file("logo.png", "hello".getBytes()));
        form.setPrimaryColor("red");

        // when / then
        assertThat(form.validate()).containsExactly(
                java.util.Map.entry("storeName", "store.branding.name.required"),
                java.util.Map.entry("logoFile", "store.branding.logo.invalid.type"),
                java.util.Map.entry("primaryColor", "store.branding.color.invalid"));
    }

    @Test
    void limitsTheStoreNameToOneHundredCharactersAfterTrimming() {
        // given
        BrandingForm atLimit = validForm();
        atLimit.setStoreName("  " + "a".repeat(100) + "  ");
        BrandingForm tooLong = validForm();
        tooLong.setStoreName("a".repeat(101));

        // when / then
        assertThat(atLimit.validate()).isEmpty();
        assertThat(tooLong.validate()).containsEntry("storeName", "store.branding.name.too.long");
    }

    @Test
    void acceptsAnImageUpToOneMegabyteRecognisedByItsContentNotItsName() {
        // given
        byte[] content = Arrays.copyOf(PNG, BrandingForm.LOGO_MAX_BYTES);
        BrandingForm form = validForm();
        form.setLogoFile(file("logo.gif", content));

        // when / then
        assertThat(form.validate()).isEmpty();
        assertThat(form.logoUpload()).hasValueSatisfying(upload -> {
            assertThat(upload.type()).isEqualTo(LogoImageType.PNG);
            assertThat(upload.content()).isEqualTo(content);
        });
    }

    @Test
    void rejectsAnImageOverOneMegabyteWithoutReadingItsContent() throws IOException {
        // given
        MultipartFile large = mock(MultipartFile.class);
        when(large.isEmpty()).thenReturn(false);
        when(large.getSize()).thenReturn(BrandingForm.LOGO_MAX_BYTES + 1L);
        BrandingForm form = validForm();
        form.setLogoFile(large);

        // when / then
        assertThat(form.validate()).containsOnlyKeys("logoFile").containsEntry("logoFile", "store.branding.logo.too.large");
        assertThat(form.logoUpload()).isEmpty();
    }

    @Test
    void treatsAnUnreadableUploadAsAnInvalidFile() throws IOException {
        // given
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(100L);
        when(broken.getBytes()).thenThrow(new IOException("connection reset"));
        BrandingForm form = validForm();
        form.setLogoFile(broken);

        // when / then
        assertThat(form.validate()).containsEntry("logoFile", "store.branding.logo.invalid.type");
    }

    @Test
    void ignoresAnEmptyFileInputAsNoLogoChosen() {
        // given
        BrandingForm form = validForm();
        form.setLogoFile(file("", new byte[0]));

        // when / then
        assertThat(form.validate()).isEmpty();
        assertThat(form.logoUpload()).isEmpty();
    }

    @Test
    void appliesATrimmedNameAndANormalizedColourKeepingTheLogo() {
        // given
        Store store = new Store();
        Branding branding = new Branding();
        branding.setLogo("store-1/logo.png");
        store.setBranding(branding);
        BrandingForm form = validForm();
        form.setStoreName("  Nowa nazwa ");
        form.setPrimaryColor("ABC");

        // when
        form.applyTo(store);

        // then
        assertThat(store.getName()).isEqualTo("Nowa nazwa");
        assertThat(store.getBranding().getPrimaryColor()).isEqualTo("#aabbcc");
        assertThat(store.getBranding().getLogo()).isEqualTo("store-1/logo.png");
    }

    @Test
    void clearsTheColourWhenTheFieldIsEmptyAndCreatesMissingBranding() {
        // given
        Store store = new Store();
        BrandingForm form = validForm();
        form.setPrimaryColor("");

        // when
        form.applyTo(store);

        // then
        assertThat(store.getBranding()).isNotNull();
        assertThat(store.getBranding().getPrimaryColor()).isNull();
    }

    @Test
    void startsFromTheStoredNameAndColour() {
        // given
        Store store = new Store();
        store.setName("Demo Store");
        Branding branding = new Branding();
        branding.setPrimaryColor("#1b4db1");
        store.setBranding(branding);

        // when
        BrandingForm form = BrandingForm.from(store);

        // then
        assertThat(form.getStoreName()).isEqualTo("Demo Store");
        assertThat(form.getPrimaryColor()).isEqualTo("#1b4db1");
        assertThat(form.isRemoveLogo()).isFalse();
    }

    @Test
    void normalizesOnlyAValidColourForThePreview() {
        // given
        BrandingForm valid = validForm();
        valid.setPrimaryColor("#1B4DB1");
        BrandingForm invalid = validForm();
        invalid.setPrimaryColor("red");

        // when / then
        assertThat(valid.getNormalizedColor()).isEqualTo("#1b4db1");
        assertThat(invalid.getNormalizedColor()).isNull();
    }
}
