package org.dbunit.dataset.excel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

class XlsDataSetWriterTest
{
    private static final File OUTPUT_DIR = Paths.get("target", "excel").toFile();

    private static final String INPUT_EXCEL_FILE =
            "/excel/XlsDataSetWriterCellStyleCaching.xlsx";

    private static final File OUTPUT_EXCEL_FILE = OUTPUT_DIR.toPath()
            .resolve("XlsDataSetWriterCellStyleCachingTestOutput.xls").toFile();

    /**
     * Test for issue 377. Without 377's changes, test fails with:
     * java.lang.IllegalStateException: The maximum number of cell styles was
     * exceeded. You can define up to 4000 styles in a .xls workbook
     */
    @Test
    void testTimestampTzOffsets()
            throws URISyntaxException, DataSetException, IOException
    {
        OUTPUT_DIR.mkdir();

        final URL excelFileUrl = getClass().getResource(INPUT_EXCEL_FILE);
        final URI excelFileUri = excelFileUrl.toURI();
        final File file = Paths.get(excelFileUri).toFile();
        final IDataSet dataSet = new XlsDataSet(file);
        final OutputStream outputStream =
                Files.newOutputStream(OUTPUT_EXCEL_FILE.toPath());
        assertDoesNotThrow(() -> XlsDataSet.write(dataSet, outputStream));
    }
}
