package pl.commercelink.web.orders;

import io.nayuki.qrcodegen.QrCode;

/**
 * A QR code as inline SVG for printouts: one path of dark modules on a white square, with the 4-module quiet zone
 * scanners need. The drawing has no size of its own, CSS gives it one; it is built from module coordinates only, so
 * a template may insert it unescaped.
 */
public final class QrCodeSvg {

    static final int QUIET_ZONE = 4;

    private QrCodeSvg() {
    }

    public static String of(String text) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int size = qr.size + 2 * QUIET_ZONE;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    path.append('M').append(x + QUIET_ZONE).append(',').append(y + QUIET_ZONE).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size + "\""
                + " aria-hidden=\"true\" focusable=\"false\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"" + size + "\" height=\"" + size + "\" fill=\"#fff\"/>"
                + "<path d=\"" + path + "\" fill=\"#000\"/></svg>";
    }
}
