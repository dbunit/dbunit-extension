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

package org.dbunit.ext.mssql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.Assertion;
import org.dbunit.TestFeature;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.ForwardOnlyDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.dbunit.dataset.filter.IColumnFilter;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * @author Manuel Laflamme
 * @author Eric Pugh
 * @version $Revision$
 * @since Feb 19, 2002
 */
@EnabledIfEnvironmentVariable(named = "MAVEN_CMD_LINE_ARGS", matches = "(.*)mssql(.*)")
class InsertIdentityOperationIT extends AbstractDatabaseIT
{

    @Override
    protected boolean runTest(final String testName)
    {
        return environmentHasFeature(TestFeature.INSERT_IDENTITY);
    }

    @Test
    void testExecuteXML() throws Exception
    {
        final Reader in =
                TestUtils.getFileReader("xml/insertIdentityOperationTest.xml");
        final IDataSet dataSet = new XmlDataSet(in);

        testExecute(dataSet);
    }

    @Test
    void testExecuteFlatXML() throws Exception
    {
        final Reader in = TestUtils
                .getFileReader("xml/insertIdentityOperationTestFlat.xml");
        final IDataSet dataSet = new FlatXmlDataSetBuilder().build(in);

        testExecute(dataSet);
    }

    @Test
    void testExecuteLowerCase() throws Exception
    {
        final Reader in = TestUtils
                .getFileReader("xml/insertIdentityOperationTestFlat.xml");
        final IDataSet dataSet =
                new LowerCaseDataSet(new FlatXmlDataSetBuilder().build(in));

        testExecute(dataSet);
    }

    @Test
    void testExecuteForwardOnly() throws Exception
    {
        final Reader in = TestUtils
                .getFileReader("xml/insertIdentityOperationTestFlat.xml");
        final IDataSet dataSet =
                new ForwardOnlyDataSet(new FlatXmlDataSetBuilder().build(in));

        testExecute(dataSet);
    }

    private void testExecute(final IDataSet dataSet) throws Exception
    {
        final ITable[] tablesBefore =
                DataSetUtils.getTables(_connection.createDataSet());
        // InsertIdentityOperation.CLEAN_INSERT.execute(_connection, dataSet);
        InsertIdentityOperation.INSERT.execute(_connection, dataSet);
        final ITable[] tablesAfter =
                DataSetUtils.getTables(_connection.createDataSet());

        assertThat(tablesAfter).as("table count").hasSameSizeAs(tablesBefore);

        // Verify tables after
        for (int i = 0; i < tablesAfter.length; i++)
        {
            final ITable tableBefore = tablesBefore[i];
            final ITable tableAfter = tablesAfter[i];

            final String name = tableAfter.getTableMetaData().getTableName();
            if (name.startsWith("IDENTITY"))
            {
                assertThat(tableBefore.getRowCount())
                        .as("row count before: " + name).isZero();
                if (dataSet instanceof ForwardOnlyDataSet)
                {
                    assertThat(tableAfter.getRowCount()).as(name).isPositive();
                } else
                {
                    Assertion.assertEquals(dataSet.getTable(name), tableAfter);
                }
            } else
            {
                // Other tables should have not been affected
                Assertion.assertEquals(tableBefore, tableAfter);
            }
        }
    }

    /*
     * test case was added to validate the bug that tables with Identity columns
     * that are not one of the primary keys are able to figure out if an
     * IDENTITY_INSERT is needed. Thanks to Gaetano Di Gregorio for finding the
     * bug.
     */
    public void testIdentityInsertNoPK() throws Exception
    {
        final Reader in = TestUtils
                .getFileReader("xml/insertIdentityOperationTestNoPK.xml");
        final IDataSet xmlDataSet = new FlatXmlDataSetBuilder().build(in);

        final ITable[] tablesBefore =
                DataSetUtils.getTables(_connection.createDataSet());
        InsertIdentityOperation.CLEAN_INSERT.execute(_connection, xmlDataSet);
        final ITable[] tablesAfter =
                DataSetUtils.getTables(_connection.createDataSet());

        // Verify tables after
        for (int i = 0; i < tablesAfter.length; i++)
        {
            final ITable tableBefore = tablesBefore[i];
            final ITable tableAfter = tablesAfter[i];

            final String name = tableAfter.getTableMetaData().getTableName();
            if (name.equals("TEST_IDENTITY_NOT_PK"))
            {
                assertThat(tableBefore.getRowCount())
                        .as("row count before: " + name).isZero();
                Assertion.assertEquals(xmlDataSet.getTable(name), tableAfter);
            } else
            {
                // Other tables should have not been affected
                Assertion.assertEquals(tableBefore, tableAfter);
            }
        }
    }

    public void testSetCustomIdentityColumnFilter() throws Exception
    {
        _connection.getConfig().setProperty(
                DatabaseConfig.PROPERTY_IDENTITY_COLUMN_FILTER,
                IDENTITY_FILTER_INVALID);
        try
        {
            final IDataSet dataSet = _connection.createDataSet();
            final ITable table = dataSet.getTable("IDENTITY_TABLE");

            InsertIdentityOperation op =
                    new InsertIdentityOperation(DatabaseOperation.INSERT);
            boolean hasIdentityColumn =
                    op.hasIdentityColumn(table.getTableMetaData(), _connection);
            assertThat(hasIdentityColumn).as("Identity column recognized")
                    .isFalse();

            // Verify that identity column is still correctly recognized with
            // default identityColumnFilter
            _connection.getConfig().setProperty(
                    DatabaseConfig.PROPERTY_IDENTITY_COLUMN_FILTER, null);
            op = new InsertIdentityOperation(DatabaseOperation.INSERT);
            hasIdentityColumn =
                    op.hasIdentityColumn(table.getTableMetaData(), _connection);
            assertThat(hasIdentityColumn).as("Identity column not recognized")
                    .isTrue();
        } finally
        {
            // Reset property
            _connection.getConfig().setProperty(
                    DatabaseConfig.PROPERTY_IDENTITY_COLUMN_FILTER, null);
        }
    }

    private static final IColumnFilter IDENTITY_FILTER_INVALID =
            new IColumnFilter()
            {

                @Override
                public boolean accept(final String tableName,
                        final Column column)
                {
                    return column.getSqlTypeName().endsWith("invalid");
                }
            };

}
