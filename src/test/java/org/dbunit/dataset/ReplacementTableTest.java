/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Date;

import org.dbunit.Assertion;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Manuel Laflamme
 * @since Mar 17, 2003
 * @version $Revision$
 */
class ReplacementTableTest extends AbstractTableTest
{

    @Override
    protected ITable createTable() throws Exception
    {
        return createDataSet().getTable("TEST_TABLE");
    }

    private IDataSet createDataSet() throws Exception
    {
        final FlatXmlDataSet fds = new FlatXmlDataSetBuilder()
                .build(TestUtils.getFile("xml/flatXmlTableTest.xml"));
        return new ReplacementDataSet(fds);
    }

    @Override
    public void testGetMissingValue() throws Exception
    {
        // TODO test something usefull
    }

    @Test
    void testObjectReplacement() throws Exception
    {
        final String tableName = "TABLE_NAME";
        final BigDecimal trueObject = new BigDecimal((double) 1);
        final BigDecimal falseObject = new BigDecimal((double) 0);
        final Date now = new Date(System.currentTimeMillis());

        final Column[] columns =
                new Column[] {new Column("BOOLEAN_TRUE", DataType.BOOLEAN),
                        new Column("BOOLEAN_FALSE", DataType.BOOLEAN),
                        new Column("STRING_TRUE", DataType.CHAR),
                        new Column("STRING_FALSE", DataType.CHAR),
                        new Column("STRING_VALUE", DataType.CHAR),
                        new Column("DATE_VALUE", DataType.DATE),
                        new Column("NULL_TO_STRING_VALUE", DataType.CHAR),
                        new Column("STRING_TO_NULL_VALUE", DataType.CHAR),};

        // Setup actual table
        final Object[] actualRow = new Object[] {Boolean.TRUE, Boolean.FALSE,
                Boolean.TRUE.toString(), Boolean.FALSE.toString(), "value",
                "now", null, "null",};

        final DefaultTable originalTable = new DefaultTable(tableName, columns);
        originalTable.addRow(actualRow);
        final ReplacementTable actualTable =
                new ReplacementTable(originalTable);
        actualTable.addReplacementObject(Boolean.TRUE, trueObject);
        actualTable.addReplacementObject(Boolean.FALSE, falseObject);
        actualTable.addReplacementObject("now", now);
        actualTable.addReplacementObject("null", null);
        actualTable.addReplacementObject(null, "nullreplacement");

        // Setup expected table
        final Object[] expectedRow = new Object[] {trueObject, falseObject,
                Boolean.TRUE.toString(), Boolean.FALSE.toString(), "value", now,
                "nullreplacement", null,};

        final DefaultTable expectedTable = new DefaultTable(tableName, columns);
        expectedTable.addRow(expectedRow);
        Assertion.assertEquals(expectedTable, actualTable);
    }

    @Test
    void testSubstringReplacement() throws Exception
    {
        final String tableName = "TABLE_NAME";

        final Column[] columns =
                new Column[] {new Column("ONLY_SUBSTRING", DataType.CHAR),
                        new Column("START_SUBSTRING", DataType.CHAR),
                        new Column("MIDDLE_SUBSTRING", DataType.CHAR),
                        new Column("END_SUBSTRING", DataType.CHAR),
                        new Column("MULTIPLE_SUBSTRING", DataType.CHAR),
                        new Column("NO_SUBSTRING", DataType.CHAR),
                        new Column("NOT_A_STRING", DataType.NUMERIC),
                        new Column("NULL_VALUE", DataType.CHAR),};

        // Setup actual table
        final Object[] actualRow = new Object[] {"substring", "substring_",
                "_substring_", "_substring", "substringsubstring substring",
                "this is a string", Long.valueOf(0), null,};

        final DefaultTable originalTable = new DefaultTable(tableName, columns);
        originalTable.addRow(actualRow);
        final ReplacementTable actualTable =
                new ReplacementTable(originalTable);
        actualTable.addReplacementSubstring("substring", "replacement");

        // Setup expected table
        final Object[] expectedRow =
                new Object[] {"replacement", "replacement_", "_replacement_",
                        "_replacement", "replacementreplacement replacement",
                        "this is a string", Long.valueOf(0), null,};

        final DefaultTable expectedTable = new DefaultTable(tableName, columns);
        expectedTable.addRow(expectedRow);

        Assertion.assertEquals(expectedTable, actualTable);
    }

    /**
     * Tests that replacement will fail properly when strict replacement fails.
     */
    @Test
    void testStrictReplacement() throws Exception
    {
        final String tableName = "TABLE_NAME";
        final String replacedColumnName = "REPLACED_COLUMN";
        final String notReplacedColumnName = "NOT_REPLACED_COLUMN";

        final String replacedValue = "replacement";
        final String notReplacedValue = "badstring";
        final String notReplacedDelimitedValue = "${" + notReplacedValue + "}";

        final Column[] columns =
                new Column[] {new Column(replacedColumnName, DataType.CHAR),
                        new Column(notReplacedColumnName, DataType.CHAR),};

        // Setup actual table
        final Object[] actualRow =
                new Object[] {"${substring}", notReplacedDelimitedValue,};

        final DefaultTable originalTable = new DefaultTable(tableName, columns);
        originalTable.addRow(actualRow);
        final ReplacementTable actualTable =
                new ReplacementTable(originalTable);
        actualTable.addReplacementSubstring("substring", replacedValue);
        actualTable.setSubstringDelimiters("${", "}");

        // Setup expected table
        final Object[] expectedRow =
                new Object[] {replacedValue, notReplacedDelimitedValue,};

        final DefaultTable expectedTable = new DefaultTable(tableName, columns);
        expectedTable.addRow(expectedRow);

        Assertion.assertEquals(expectedTable, actualTable);

        String foundReplaced =
                (String) actualTable.getValue(0, replacedColumnName);
        assertThat(foundReplaced).isEqualTo(replacedValue);

        // we should get back the non-replaced value with the delimiters in it
        final String foundNotReplaced =
                (String) actualTable.getValue(0, notReplacedColumnName);
        assertThat(foundNotReplaced).isEqualTo(notReplacedDelimitedValue);

        // prior to this, it was just testing that it hooks up properly.
        // now try some tests with the strict replacement set
        actualTable.setStrictReplacement(true);

        // this should still succeed
        foundReplaced = (String) actualTable.getValue(0, replacedColumnName);
        assertThat(foundReplaced).isEqualTo(replacedValue);

        // this should fail
        assertThrows(DataSetException.class,
                () -> actualTable.getValue(0, notReplacedColumnName),
                "Expecting a DataSetException");

        // try again after adding the badstring as a replacement
        final String replacedValue2 = "replacement2";
        actualTable.addReplacementSubstring(notReplacedValue, replacedValue2);
        foundReplaced = (String) actualTable.getValue(0, notReplacedColumnName);
        assertThat(foundReplaced).isEqualTo(replacedValue2);
    }

    @Test
    void testDelimitedSubstringReplacement() throws Exception
    {
        final String tableName = "TABLE_NAME";

        final Column[] columns = new Column[] {
                new Column("ONLY_SUBSTRING", DataType.CHAR),
                new Column("START_SUBSTRING", DataType.CHAR),
                new Column("MIDDLE_SUBSTRING", DataType.CHAR),
                new Column("END_SUBSTRING", DataType.CHAR),
                new Column("MULTIPLE_SUBSTRING", DataType.CHAR),
                new Column("NO_SUBSTRING", DataType.CHAR),
                new Column("NOT_A_STRING", DataType.NUMERIC),
                new Column("NULL_VALUE", DataType.CHAR),
                new Column("ONLY_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("START_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("MIDDLE_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("END_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("MULTIPLE_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING1", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING2", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING3", DataType.CHAR),
                // new Column("BAD_DELIMITED_SUBSTRING4", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING5", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING6", DataType.CHAR),
                new Column("BAD_SUBSTRING1", DataType.CHAR),
                new Column("BAD_SUBSTRING2", DataType.CHAR),};

        // Setup actual table
        final Object[] actualRow = new Object[] {"${substring}",
                "${substring}_", "_${substring}_", "_${substring}",
                "${substring}${substring} ${substring}", "this is a string",
                Long.valueOf(0), null, "substring", "substring_", "_substring_",
                "_substring", "substringsubstring substring", "_${substring_",
                "_$substring}_", "_substring}_", "}", "${",
                // "${substring${substring} ${substring}", - Should we support
                // this???
                "${substringsubstring}${}${}${substring}${}_", "${}",};

        final DefaultTable originalTable = new DefaultTable(tableName, columns);
        originalTable.addRow(actualRow);
        final ReplacementTable actualTable =
                new ReplacementTable(originalTable);
        actualTable.addReplacementSubstring("substring", "replacement");
        actualTable.setSubstringDelimiters("${", "}");

        // Setup expected table
        final Object[] expectedRow = new Object[] {"replacement",
                "replacement_", "_replacement_", "_replacement",
                "replacementreplacement replacement", "this is a string",
                Long.valueOf(0), null, "substring", "substring_", "_substring_",
                "_substring", "substringsubstring substring", "_${substring_",
                "_$substring}_", "_substring}_", "}", "${",
                // "${substringreplacement replacement",
                "${substringsubstring}${}${}replacement${}_", "${}",};

        final DefaultTable expectedTable = new DefaultTable(tableName, columns);
        expectedTable.addRow(expectedRow);

        Assertion.assertEquals(expectedTable, actualTable);
    }

    @Test
    void testDelimitedSubstringReplacementWithIdenticalDelimiters()
            throws Exception
    {
        final String tableName = "TABLE_NAME";

        final Column[] columns = new Column[] {
                new Column("ONLY_SUBSTRING", DataType.CHAR),
                new Column("START_SUBSTRING", DataType.CHAR),
                new Column("MIDDLE_SUBSTRING", DataType.CHAR),
                new Column("END_SUBSTRING", DataType.CHAR),
                new Column("MULTIPLE_SUBSTRING", DataType.CHAR),
                new Column("NO_SUBSTRING", DataType.CHAR),
                new Column("NOT_A_STRING", DataType.NUMERIC),
                new Column("NULL_VALUE", DataType.CHAR),
                new Column("ONLY_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("START_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("MIDDLE_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("END_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("MULTIPLE_NONDELIMITED_SUBSTRING", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING1", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING2", DataType.CHAR),
                // new Column("BAD_DELIMITED_SUBSTRING4", DataType.CHAR),
                new Column("BAD_DELIMITED_SUBSTRING5", DataType.CHAR),
                new Column("BAD_SUBSTRING1", DataType.CHAR),
                new Column("BAD_SUBSTRING2", DataType.CHAR),};

        // Setup actual table
        final Object[] actualRow = new Object[] {"!substring!", "!substring!_",
                "_!substring!_", "_!substring!",
                "!substring!!substring! !substring!", "this is a string",
                Long.valueOf(0), null, "substring", "substring_", "_substring_",
                "_substring", "substringsubstring substring", "_!substring_",
                "_substring!_", "!",
                // "!substring!substring! !substring!", - Should we support
                // this???
                "!substringsubstring!!!!!!substring!!!_", "!!",};

        final DefaultTable originalTable = new DefaultTable(tableName, columns);
        originalTable.addRow(actualRow);
        final ReplacementTable actualTable =
                new ReplacementTable(originalTable);
        actualTable.addReplacementSubstring("substring", "replacement");
        actualTable.setSubstringDelimiters("!", "!");

        // Setup expected table
        final Object[] expectedRow = new Object[] {"replacement",
                "replacement_", "_replacement_", "_replacement",
                "replacementreplacement replacement", "this is a string",
                Long.valueOf(0), null, "substring", "substring_", "_substring_",
                "_substring", "substringsubstring substring", "_!substring_",
                "_substring!_", "!",
                // "!substringreplacement replacement",
                "!substringsubstring!!!!!replacement!!_", "!!",};

        final DefaultTable expectedTable = new DefaultTable(tableName, columns);
        expectedTable.addRow(expectedRow);

        Assertion.assertEquals(expectedTable, actualTable);
    }

    @Test
    void testAddNullReplacementSubstring() throws Exception
    {
        final ReplacementTable replacementTable =
                new ReplacementTable(new DefaultTable("TABLE"));
        assertThrows(
                NullPointerException.class, () -> replacementTable
                        .addReplacementSubstring(null, "replacement"),
                "Should not be here!");

        assertThrows(
                NullPointerException.class, () -> replacementTable
                        .addReplacementSubstring("substring", null),
                "Should not be here!");
    }

}
