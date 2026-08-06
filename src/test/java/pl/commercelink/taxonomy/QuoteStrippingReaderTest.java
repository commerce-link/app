package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteStrippingReaderTest {

    @Test
    void stripsLiteralQuotesLineByLine() throws IOException {
        // given
        String raw = "a;\"b\";c\nd;\"e\"\"f\";g\n";

        // when
        String actual = readFully(new QuoteStrippingReader(new StringReader(raw)), 7);

        // then
        assertThat(actual).isEqualTo("a;b;c" + System.lineSeparator() + "d;ef;g" + System.lineSeparator());
    }

    @Test
    void survivesReadsSmallerThanALine() throws IOException {
        // given: force a caller to pull one character at a time, to catch bugs
        // at the internal line-buffer boundary
        String raw = "8596049159455;60312151000003;Epico;\"Szklo \"\"ochronne\"\"\";Category;1568;3;;\n";

        // when
        StringWriter out = new StringWriter();
        QuoteStrippingReader reader = new QuoteStrippingReader(new StringReader(raw));
        int c;
        while ((c = reader.read()) != -1) {
            out.write(c);
        }

        // then
        assertThat(out.toString()).isEqualTo(raw.replace("\"", ""));
    }

    @Test
    void handlesInputWithoutTrailingNewline() throws IOException {
        // given
        String raw = "a;\"b\";c";

        // when
        String actual = readFully(new QuoteStrippingReader(new StringReader(raw)), 3);

        // then
        assertThat(actual).isEqualTo("a;b;c" + System.lineSeparator());
    }

    private static String readFully(java.io.Reader reader, int bufferSize) throws IOException {
        StringWriter out = new StringWriter();
        char[] buffer = new char[bufferSize];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString();
    }
}
