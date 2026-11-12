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
     * @return The columns in the table's order, or null when the connection
     *         has no JDBC connection to read through.
     * @throws DataSetException
     *             If the select fails, for example for a name that is no
     *             table. It is not swallowed: on a connection with autocommit
     *             off, a failed statement can leave the owner's transaction
     *             unusable, so the failure is reported here where it
     *             happened, with the driver's exception as the cause.
     */
    Column[] read(final String tableName) throws DataSetException
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
        } catch (final SQLException e)
        {
            final String msg = "read: Problem obtaining the columns of table '"
                    + tableName + "' from database";
            throw new DataSetException(msg, e);
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
