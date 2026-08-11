package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Reproduces the second scenario of issue 397: an {@link IDatabaseConnection} configured with a
 * non-default schema and given an <i>unqualified</i> table name, without
 * {@link DatabaseConfig#FEATURE_QUALIFIED_TABLE_NAMES} enabled. The original report saw
 * {@code DatabaseOperation.DELETE_ALL} generate {@code delete from app_role_all_children},
 * missing the {@code bi} schema, failing with "relation ... does not exist" since the
 * connection's own default schema was never applied to the generated SQL.
 * <p>
 * The report's other scenario - a schema-qualified table name failing to resolve - is a
 * case-folding defect covered separately by {@link PostgresqlUppercaseSchemaIT} (issue 656).
 *
 * @author Jeff Jensen
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class PostgresqlNonPublicSchemaDeleteAllIT
{
    private static final String SCHEMA = "bi";
    private static final String TABLE = "app_role_all_children";

    private IDatabaseConnection _connection;

    @BeforeEach
    protected void setUp() throws Exception
    {
        final IDatabaseConnection defaultConnection = DatabaseEnvironment.getInstance().getConnection();
        try (Statement statement = defaultConnection.getConnection().createStatement())
        {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE;");
            statement.execute("CREATE SCHEMA " + SCHEMA + ";");
            statement.execute("CREATE TABLE " + SCHEMA + "." + TABLE
                    + "(id INTEGER NOT NULL, name VARCHAR(32));");
            statement.execute("INSERT INTO " + SCHEMA + "." + TABLE
                    + " (id, name) VALUES (1, 'root'), (2, 'child');");
        }

        _connection = new DatabaseConnection(defaultConnection.getConnection(), SCHEMA);
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        if (_connection != null)
        {
            try (Statement statement = _connection.getConnection().createStatement())
            {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE;");
            }
            _connection.close();
            _connection = null;
        }
    }

    @Test
    void testDeleteAll_withUnqualifiedTableNameAndNonPublicConnectionSchema_deletesAllRows()
            throws Exception
    {
        final IDataSet dataSet = new DefaultDataSet(new DefaultTable(TABLE));

        DatabaseOperation.DELETE_ALL.execute(_connection, dataSet);

        final int remainingRows;
        try (Statement statement = _connection.getConnection().createStatement();
                ResultSet resultSet =
                        statement.executeQuery("select count(*) from " + SCHEMA + "." + TABLE))
        {
            resultSet.next();
            remainingRows = resultSet.getInt(1);
        }

        assertThat(remainingRows).as("remaining row count.").isEqualTo(0);
    }
}
