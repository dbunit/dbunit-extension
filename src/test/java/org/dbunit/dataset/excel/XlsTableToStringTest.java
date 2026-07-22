package org.dbunit.dataset.excel;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;

/**
 * Asserts the toString() representation of a real XlsTable. XlsTable is
 * package-private, so this lives in the same package to obtain an instance
 * via XlsTableTest's existing fixture.
 *
 * @see org.dbunit.dataset.ToStringViewTest
 */
class XlsTableToStringTest
{
    @Test
    void testXlsTable_withXlsTable_hasExpectedToString() throws Exception
    {
        final XlsTableTest test = new XlsTableTest();
        final ITable table = test.createTable();

        final String expectedPrefix =
                "org.dbunit.dataset.excel.XlsTable[_metaData=tableName=TEST_TABLE, columns=["
                        + "(COLUMN0, UNKNOWN, nullableUnknown), "
                        + "(COLUMN1, UNKNOWN, nullableUnknown), "
                        + "(COLUMN2, UNKNOWN, nullableUnknown), "
                        + "(COLUMN3, UNKNOWN, nullableUnknown)], keys=[], _sheet=";

        // _sheet and symbols embed the POI Sheet/DecimalFormatSymbols default
        // Object.toString(), which includes a non-deterministic identity
        // hashcode, so only their stable prefix/markers are asserted.
        assertThat(table.toString())
                .as("XlsTable.toString() should start with the expected, stable representation.")
                .startsWith(expectedPrefix)
                .as("XlsTable.toString() should include the symbols marker.")
                .contains(", symbols=java.text.DecimalFormatSymbols@");
    }
}
