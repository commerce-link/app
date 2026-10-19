package pl.commercelink.web.orders;

import io.nayuki.qrcodegen.QrCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The printed QR code: exactly the modules of the encoded text, on white, with the 4-module quiet zone scanners need. */
class QrCodeSvgTest {

    private static final String URL = "https://app.commercelink.pl/dashboard/scan/orders/uma2dqukxr/3e373abc-1111-2222-3333-444455556666";

    @Test
    void drawsEveryDarkModuleOfTheEncodedTextAndNothingElse() {
        // given
        QrCode qr = QrCode.encodeText(URL, QrCode.Ecc.MEDIUM);
        int dark = 0;
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    dark++;
                }
            }
        }

        // when
        String svg = QrCodeSvg.of(URL);

        // then
        assertThat(svg.split("h1v1h-1z", -1).length - 1).isEqualTo(dark);
    }

    @Test
    void leavesAFourModuleQuietZoneAroundTheCode() {
        // given
        int size = QrCode.encodeText(URL, QrCode.Ecc.MEDIUM).size;

        // when
        String svg = QrCodeSvg.of(URL);

        // then: the top-left finder pattern starts at (4,4)
        assertThat(svg).contains("viewBox=\"0 0 " + (size + 8) + " " + (size + 8) + "\"")
                .contains("M4,4h1v1h-1z");
    }

    @Test
    void isSizedByCssAndHiddenFromScreenReaders() {
        // when
        String svg = QrCodeSvg.of(URL);

        // then: the wrapper in the template carries the accessible name
        assertThat(svg).startsWith("<svg ").contains("aria-hidden=\"true\"").contains("shape-rendering=\"crispEdges\"")
                .contains("fill=\"#fff\"").contains("fill=\"#000\"")
                .doesNotContain("width=\"1").doesNotContain("px");
    }

    @Test
    void theSameTextGivesTheSameDrawing() {
        // when / then
        assertThat(QrCodeSvg.of(URL)).isEqualTo(QrCodeSvg.of(URL));
    }
}
