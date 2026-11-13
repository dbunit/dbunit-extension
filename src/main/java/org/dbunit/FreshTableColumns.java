package org.dbunit;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.ResultSetTableMetaData;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.util.QualifiedTableName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the columns of a table from the database as a comparison of the table
 * sees them, without fetching a row: a select that returns no row gives the
 * same {@link ResultSetTableMetaData} that
 * {@link IDatabaseConnection#createTable(String)} gives. Unlike the
 * connection's cached {@link org.dbunit.database.DatabaseDataSet}, it keeps a
 * column of a type the data type factory does not know, and sees a column
 * added after the dataset first read the table.
 *
 * <p>
 * This class is internal to dbUnit's test cases; do not use it directly.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class FreshTableColumns
{
    private static final Logger log =
            LoggerFactory.getLogger(FreshTableColumns.class);

    private final IDatabaseConnection connection;

    /**
     * Creates a reader that reads through the given connection.
     *
     * @param connection
     *            The connection to read the table's columns through.
     */
    FreshTableColumns(final IDatabaseConnection connection)
    {
        this.connection = connection;
    }

    /**
     * Reads the columns of the table.
     *
     * @param tableName
     *            The name of the table, as the verify table definition gives
     *            it. This reader qualifies it with the connection's schema when
     *            it names none and escapes it as the connection is configured.
     * @return The columns in the table's order, or null when they cannot be
     *         read, for example for a name that is no table.
     */
    Column[] read(final String tableName)
    {
        try
        {
            final Connection jdbcConnection = connection.getConnection();
            if (jdbcConnection == null)
            {
                return null;
            }

            final String sql = makeSelectWithoutRows(tableName);
            try (final Statement statement = jdbcConnection.createStatement();
                    final ResultSet resultSet = statement.executeQuery(sql))
            {
                final ResultSetTableMetaData metaData =
                        new ResultSetTableMetaData(tableName, resultSet,
                                connection, false);
                return metaData.getColumns();
            }
        } catch (final SQLException | DataSetException e)
        {
            log.debug("read: Cannot determine the columns of table {}",
                    tableName, e);
            return null;
        }
    }

    private String makeSelectWithoutRows(final String tableName)
    {
        final String escapePattern = (String) connection.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN);
        final QualifiedTableName qualifiedTableName = new QualifiedTableName(
                tableName, connection.getSchema(), escapePattern);
        final String qualifiedName = qualifiedTableName.getQualifiedName();
        return "select * from " + qualifiedName + " where 1=0";
    }
}
