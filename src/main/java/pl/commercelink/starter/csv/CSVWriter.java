package pl.commercelink.starter.csv;

import java.io.*;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class CSVWriter {

    public void writeAllRows(List<? extends CSVReady> rows, String file) throws IOException {
        writeAllRows(rows, null, file);
    }

    public void writeAllRows(List<? extends CSVReady> rows, String[] headers, String file) throws IOException {
        File f = new File(file);

        List<String[]> data = rows.stream().map(CSVReady::asStringArray).collect(Collectors.toList());
        writeToOutputStream(data, headers, new FileWriter(f));

        System.out.println(f.getAbsolutePath());
    }

    public byte[] writeAllRowsToBytes(Collection<? extends CSVReady> rows, String[] headers) throws IOException {
        List<String[]> data = rows.stream().map(CSVReady::asStringArray).collect(Collectors.toList());
        return writeAllRowsRawToBytes(data, headers);
    }

    private byte[] writeAllRowsRawToBytes(List<String[]> data, String[] headers) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        writeToOutputStream(data, headers, outputStreamWriter);

        return byteArrayOutputStream.toByteArray();
    }

    private void writeToOutputStream(List<String[]> data, String[] headers, OutputStreamWriter outputStreamWriter) throws IOException {
        com.opencsv.CSVWriter writer = new com.opencsv.CSVWriter(outputStreamWriter, ';',
                com.opencsv.CSVWriter.DEFAULT_QUOTE_CHARACTER,
                com.opencsv.CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                com.opencsv.CSVWriter.DEFAULT_LINE_END
        );

        if (headers != null) {
            List<String[]> row = new LinkedList<>();
            row.add(headers);

            writer.writeAll(row);
        }

        writer.writeAll(data);
        writer.flush();
        writer.close();
    }

}
