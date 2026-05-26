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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Statement;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.TestFeature;
import org.dbunit.dataset.AbstractTableTest;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link ScrollableResultSetTable}.
 *
 * <p>Tests inherited from {@link AbstractTableTest} are skipped when the active
 * database profile does not support {@link TestFeature#SCROLLABLE_RESULTSET}.
 *
 * <p>The H2-based tests ({@code *_withH2Connection_*}) always run and verify
 * that both constructors produce a scrollable table with correct row count and
 * random-access {@code getValue()}.  They would fail before the
 * {@code TYPE_SCROLL_INSENSITIVE} fix.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public class ScrollableResultSetTableIT extends AbstractTableTest
{
    private static final String H2_TABLE = "T";
    private static final String H2_SELECT = "SELECT * FROM T ORDER BY ID";

    private IDatabaseConnection h2Connection;

    @BeforeEach
    void setUpH2() throws Exception
    {
        h2Connection = InMemoryDatabaseConnection.create("PUBLIC");
        final Statement stmt = h2Connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE T (ID INTEGER PRIMARY KEY, NAME VARCHAR(50))");
        stmt.execute("INSERT INTO T VALUES (1, 'Alice')");
        stmt.execute("INSERT INTO T VALUES (2, 'Bob')");
        stmt.close();
    }

    @AfterEach
    void tearDownH2() throws Exception
    {
        if (h2Connection != null)
        {
            h2Connection.close();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Skips when the active database profile does not support
     * {@link TestFeature#SCROLLABLE_RESULTSET}.
     */
    @Override
    protected ITable createTable() throws Exception
    {
        assumeTrue(AbstractDatabaseIT.environmentHasFeature(TestFeature.SCROLLABLE_RESULTSET),
                "Skipping: SCROLLABLE_RESULTSET not supported by current database profile.");

        final DatabaseEnvironment env = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = env.getConnection();

        DatabaseOperation.CLEAN_INSERT.execute(connection, env.getInitDataSet());

        final String selectStatement = "select * from TEST_TABLE order by COLUMN0";
        return new ScrollableResultSetTable("TEST_TABLE", selectStatement, connection);
    }

    @Override
    public void testGetMissingValue_withMissingCells_returnsExpectedValues() throws Exception
    {
        // ScrollableResultSetTable does not have missing-cell semantics; skip this inherited test.
    }

    // -------------------------------------------------------------------------
    // H2-based tests — always run, verify the TYPE_SCROLL_INSENSITIVE fix
    // -------------------------------------------------------------------------

    @Test
    void testConstructorWithMetaDataAndConnection_withH2Connection_returnsCorrectRowCount()
            throws Exception
    {
        final ITableMetaData metaData =
                h2Connection.createDataSet().getTableMetaData(H2_TABLE);

        final ScrollableResultSetTable table =
                new ScrollableResultSetTable(metaData, h2Connection);

        assertThat(table.getRowCount()).as("row count.").isEqualTo(2);
        table.close();
    }

    @Test
    void testConstructorWithMetaDataAndConnection_withH2Connection_allowsRandomAccessToEarlierRow()
            throws Exception
    {
        final ITableMetaData metaData =
                h2Connection.createDataSet().getTableMetaData(H2_TABLE);

        final ScrollableResultSetTable table =
                new ScrollableResultSetTable(metaData, h2Connection);

        // Access row 1 first, then row 0 — requires a scrollable result set.
        assertThat(table.getValue(1, "ID")).as("row 1 ID.").isEqualTo(2);
        assertThat(table.getValue(0, "ID")).as("row 0 ID after row 1.").isEqualTo(1);
        table.close();
    }

    @Test
    void testConstructorWithSelectStatement_withH2Connection_returnsCorrectRowCount()
            throws Exception
    {
        final ScrollableResultSetTable table =
                new ScrollableResultSetTable(H2_TABLE, H2_SELECT, h2Connection);

        assertThat(table.getRowCount()).as("row count.").isEqualTo(2);
        table.close();
    }

    @Test
    void testConstructorWithSelectStatement_withH2Connection_allowsRandomAccessToEarlierRow()
            throws Exception
    {
        final ScrollableResultSetTable table =
                new ScrollableResultSetTable(H2_TABLE, H2_SELECT, h2Connection);

        // Access row 1 first, then row 0 — requires a scrollable result set.
        assertThat(table.getValue(1, "ID")).as("row 1 ID.").isEqualTo(2);
        assertThat(table.getValue(0, "ID")).as("row 0 ID after row 1.").isEqualTo(1);
        table.close();
    }
}
