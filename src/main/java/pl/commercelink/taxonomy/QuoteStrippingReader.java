package pl.commercelink.taxonomy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

class QuoteStrippingReader extends Reader {

    private final BufferedReader source;
    private String currentLine;
    private int position;
    private boolean sourceExhausted;

    QuoteStrippingReader(Reader source) {
        this.source = source instanceof BufferedReader bufferedReader ? bufferedReader : new BufferedReader(source);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) return 0;
        int written = 0;
        while (written < len) {
            if (currentLine == null || position >= currentLine.length()) {
                if (sourceExhausted) {
                    return written == 0 ? -1 : written;
                }
                String line = source.readLine();
                if (line == null) {
                    sourceExhausted = true;
                    return written == 0 ? -1 : written;
                }
                currentLine = line.replace("\"", "") + System.lineSeparator();
                position = 0;
            }
            int toCopy = Math.min(currentLine.length() - position, len - written);
            currentLine.getChars(position, position + toCopy, cbuf, off + written);
            position += toCopy;
            written += toCopy;
        }
        return written;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
