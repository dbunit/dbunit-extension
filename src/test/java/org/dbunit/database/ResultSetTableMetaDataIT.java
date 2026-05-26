package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DdlExecutor;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.IDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
class ResultSetTableMetaDataIT extends AbstractDatabaseIT
{

    /**
     * Replaces {@code _connection} with a fresh connection after test tables
     * have been dropped, so that {@code AbstractDatabaseIT.tearDown()} uses an
     * up-to-date dataset that does not include the dropped tables.
     *
     * @throws Exception
     */
    private void refreshConnection() throws Exception
    {
        _connection.close();
        _connection = getDatabaseTester().getConnection();
        setUpDatabaseConfig(_connection.getConfig());
    }

    protected IDataSet createDataSet() throws Exception
    {
        return _connection.createDataSet();
    }

    /**
     * Tests the pattern-like column retrieval from the database. DbUnit should
     * not interpret any table names as regex patterns.
     *
     * @throws Exception
     */
    @Test
    void testGetColumnsForTablesMatchingSamePattern() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(),
                "PATTERN_LIKE_TABLE_XX", "PATTERN_LIKE_TABLE_X_");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_dataset_pattern_test.sql"),
                _connection.getConnection(), false);
        try
        {
            final String tableName = "PATTERN_LIKE_TABLE_X_";
            final String[] columnNames = {"VARCHAR_COL_XUNDERSCORE"};

            final String sql = "select * from " + tableName;
            final ForwardOnlyResultSetTable resultSetTable =
                    new ForwardOnlyResultSetTable(tableName, sql, _connection);
            final ResultSetTableMetaData metaData =
                    (ResultSetTableMetaData) resultSetTable.getTableMetaData();

            final Column[] columns = metaData.getColumns();

            assertThat(columns).as("column count").hasSize(columnNames.length);

            for (int i = 0; i < columnNames.length; i++)
            {
                final Column column =
                        Columns.getColumn(columnNames[i], columns);
                assertThat(column.getColumnName()).as(columnNames[i])
                        .isEqualToIgnoringCase(columnNames[i]);
            }
        }
        finally
        {
            // Close the old connection first to release the open ResultSet cursor
            // held by ForwardOnlyResultSetTable; otherwise Derby refuses DROP TABLE.
            refreshConnection();
            DdlExecutor.dropTables(_connection.getConnection(),
                    "PATTERN_LIKE_TABLE_XX", "PATTERN_LIKE_TABLE_X_");
        }
    }

}
