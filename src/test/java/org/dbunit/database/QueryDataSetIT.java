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

package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
class QueryDataSetIT extends AbstractDataSetTest
{
    private IDatabaseConnection _connection;

    ////////////////////////////////////////////////////////////////////////////
    // TestCase class

    @BeforeEach
    protected void setUpConnection() throws Exception
    {

        final DatabaseEnvironment env = DatabaseEnvironment.getInstance();
        _connection = env.getConnection();

        DatabaseOperation.CLEAN_INSERT.execute(_connection,
                env.getInitDataSet());
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        _connection = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSetTest class

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final String[] names = getExpectedNames();

        final QueryDataSet dataSet = new QueryDataSet(_connection);
        for (int i = 0; i < names.length; i++)
        {
            final String name = names[i];
            final String query = "select * from " + name;
            dataSet.addTable(name, query);
            /*
             * if (i % 2 == 0) { String query = "select * from " + name;
             * dataSet.addTable(name, query); } else { dataSet.addTable(name); }
             */
        }
        return dataSet;
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        final QueryDataSet dataSet = new QueryDataSet(_connection);
        final String[] names = getExpectedDuplicateNames();

        // first table expect 1 row
        final String queryOneRow = "select * from only_pk_table";
        dataSet.addTable(names[0], queryOneRow);

        // second table expect 0 row
        final String queryNoRow = "select * from empty_table";
        dataSet.addTable(names[1], queryNoRow);

        // third table expect 2 row
        final String queryTwoRow =
                "select * from pk_table where PK0=0 or PK0=1";
        dataSet.addTable(names[2], queryTwoRow);

        return dataSet;
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        final QueryDataSet dataSet = new QueryDataSet(_connection);
        final String[] names = getExpectedDuplicateNames();

        // first table expect 1 row
        final String queryOneRow = "select * from only_pk_table";
        dataSet.addTable(names[0], queryOneRow);

        // second table expect 0 row
        final String queryNoRow = "select * from empty_table";
        dataSet.addTable(names[1], queryNoRow);

        // third table expect 2 row
        final String queryTwoRow =
                "select * from pk_table where PK0=0 or PK0=1";
        dataSet.addTable(names[2].toLowerCase(), queryTwoRow); // lowercase
                                                               // table name
                                                               // which should
                                                               // fail as well

        return dataSet;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Test methods

    @Test
    void testGetSelectPartialData() throws Exception
    {

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("PK_TABLE",
                "SELECT PK0, PK1 FROM PK_TABLE where PK0 = 0");

        final ITable table = ptds.getTable("PK_TABLE");
        assertThat(table.getValue(0, "PK0")).hasToString("0");
        assertThat(new String(table.getRowCount() + "")).hasSize(1);

    }

    @Test
    void testGetAllColumnsWithStar() throws Exception
    {

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("PK_TABLE", "SELECT * FROM PK_TABLE where PK0 = 0");

        final ITable table = ptds.getTable("PK_TABLE");
        assertThat(table.getValue(0, "PK0")).hasToString("0");
        assertThat(new String(table.getRowCount() + "")).hasSize(1);

    }

    @Test
    void testGetAllRowsSingleColumn() throws Exception
    {

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("PK_TABLE", "SELECT PK0 FROM PK_TABLE ORDER BY PK0");

        final ITable table = ptds.getTable("PK_TABLE");
        assertThat(table.getValue(0, "PK0")).hasToString("0");
        assertThat(new String(table.getRowCount() + "")).isEqualTo("3");
    }

    @Test
    void testOnlySpecifiedColumnsReturned() throws Exception
    {

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("PK_TABLE", "SELECT PK0 FROM PK_TABLE ORDER BY PK0 ASC");

        final ITable table = ptds.getTable("PK_TABLE");
        assertThat(table.getValue(0, "PK0")).hasToString("0");

        final NoSuchColumnException nsce = assertThrows(
                NoSuchColumnException.class,
                () -> table.getValue(0, "PK1").toString(),
                "Should not have reached here, we should have thrown a NoSuchColumnException");
        final String errorMsg =
                "org.dbunit.dataset.NoSuchColumnException: PK_TABLE.PK1";
        assertThat(nsce).as("Find text:" + errorMsg).asString()
                .contains(errorMsg);
    }

    @Test
    void testGetSelectPartialData2() throws Exception
    {

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("SECOND_TABLE",
                "SELECT * FROM SECOND_TABLE where COLUMN0='row 0 col 0'");

        final ITable table = ptds.getTable("SECOND_TABLE");
        assertThat(table.getValue(0, "COLUMN0")).hasToString("row 0 col 0");
        assertThat(table.getValue(0, "COLUMN3")).hasToString("row 0 col 3");
        assertThat(new String(table.getRowCount() + "")).hasSize(1);

    }

    @Test
    void testCombinedWhere() throws Exception
    {

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("SECOND_TABLE",
                "SELECT COLUMN0, COLUMN3 FROM SECOND_TABLE where COLUMN0='row 0 col 0' and COLUMN2='row 0 col 2'");

        final ITable table = ptds.getTable("SECOND_TABLE");
        assertThat(table.getValue(0, "COLUMN0")).hasToString("row 0 col 0");
        assertThat(table.getValue(0, "COLUMN3")).hasToString("row 0 col 3");
        assertThat(new String(table.getRowCount() + "")).hasSize(1);

    }

    @Test
    void testMultipleTables() throws Exception
    {
        ITable table = null;

        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("SECOND_TABLE",
                "SELECT * from SECOND_TABLE where COLUMN0='row 0 col 0' and COLUMN2='row 0 col 2'");
        ptds.addTable("PK_TABLE", "SELECT * FROM PK_TABLE where PK0 = 0");

        table = ptds.getTable("SECOND_TABLE");
        assertThat(table.getValue(0, "COLUMN0")).hasToString("row 0 col 0");
        assertThat(table.getValue(0, "COLUMN3")).hasToString("row 0 col 3");
        assertThat(new String(table.getRowCount() + "")).hasSize(1);

        table = ptds.getTable("PK_TABLE");
        assertThat(table.getValue(0, "PK0").toString()).isEqualTo("0");
        assertThat(new String(table.getRowCount() + "")).isEqualTo("1");

    }

    @Test
    void testMultipleTablesWithMissingWhere() throws Exception
    {
        final QueryDataSet ptds = new QueryDataSet(_connection);
        ptds.addTable("SECOND_TABLE",
                "SELECT * from SECOND_TABLE where COLUMN0='row 0 col 0' and COLUMN2='row 0 col 2'");
        ptds.addTable("PK_TABLE", null);
    }
}
