package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DdlExecutor;
import org.dbunit.HypersonicEnvironment;
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
        final Connection jdbcConnection =
                HypersonicEnvironment.createJdbcConnection("tempdb");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_dataset_pattern_test.sql"),
                jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(jdbcConnection);

        try
        {
            final String tableName = "PATTERN_LIKE_TABLE_X_";
            final String[] columnNames = {"VARCHAR_COL_XUNDERSCORE"};

            final String sql = "select * from " + tableName;
            final ForwardOnlyResultSetTable resultSetTable =
                    new ForwardOnlyResultSetTable(tableName, sql, connection);
            final ResultSetTableMetaData metaData =
                    (ResultSetTableMetaData) resultSetTable.getTableMetaData();

            final Column[] columns = metaData.getColumns();

            assertThat(columns).as("column count").hasSize(columnNames.length);

            for (int i = 0; i < columnNames.length; i++)
            {
                final Column column =
                        Columns.getColumn(columnNames[i], columns);
                assertThat(column.getColumnName()).as(columnNames[i])
                        .isEqualTo(columnNames[i]);
            }
        } finally
        {
            HypersonicEnvironment.shutdown(jdbcConnection);
            jdbcConnection.close();
            HypersonicEnvironment.deleteFiles("tempdb");
        }
    }

}
