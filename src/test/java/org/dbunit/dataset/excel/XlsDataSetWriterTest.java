package org.dbunit.dataset.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.dbunit.dataset.DataSetBuilder;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

class XlsDataSetWriterTest
{
    private static final File OUTPUT_DIR = new File("target", "excel");

    private static final String INPUT_EXCEL_FILE =
            "/excel/XlsDataSetWriterCellStyleCaching.xlsx";

    private static final File OUTPUT_EXCEL_FILE = new File(OUTPUT_DIR,
            "XlsDataSetWriterCellStyleCachingTestOutput.xls");

    /**
     * Test for issue 377. Without 377's changes, test fails with:
     * java.lang.IllegalStateException: The maximum number of cell styles was
     * exceeded. You can define up to 4000 styles in a .xls workbook
     */
    @Test
    void testTimestampTzOffsets_withManyRowsExceedingCellStyles_doesNotThrowException()
            throws URISyntaxException, DataSetException, IOException
    {
        OUTPUT_DIR.mkdir();

        final URL excelFileUrl = getClass().getResource(INPUT_EXCEL_FILE);
        final URI excelFileUri = excelFileUrl.toURI();
        final File file = new File(excelFileUri);
        final IDataSet dataSet = new XlsDataSet(file);
        final FileOutputStream outputStream =
                new FileOutputStream(OUTPUT_EXCEL_FILE);
        assertDoesNotThrow(() -> XlsDataSet.write(dataSet, outputStream));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWrite_afterCompletion_releasesWorkbookStyles() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder().table("T")
                .columns("AMOUNT").row(new BigDecimal("1.50")).build();

        final XlsDataSetWriter writer = new XlsDataSetWriter();
        writer.write(dataSet, new ByteArrayOutputStream());

        final Field field =
                XlsDataSetWriter.class.getDeclaredField("cellStyleMap");
        field.setAccessible(true);
        final Map<Workbook, Map> cellStyleMap = (Map<Workbook, Map>) field.get(null);

        assertThat(cellStyleMap)
                .as("cellStyleMap should not retain any workbook's cell styles after write() returns.")
                .isEmpty();
    }
}
