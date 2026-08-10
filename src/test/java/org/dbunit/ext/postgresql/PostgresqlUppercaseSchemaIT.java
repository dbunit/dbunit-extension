package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.sql.Statement;
import java.util.Objects;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xml.sax.InputSource;

/**
 * Reproduces issue 656: PostgreSQL folds an unquoted schema name to lower case at creation
 * time, so a {@code FlatXmlDataSet} row qualified with a differently-cased schema (e.g. the
 * originally reported {@code CORE.USER}, matching a live {@code core} schema) must still be
 * found once {@link DatabaseConfig#FEATURE_QUALIFIED_TABLE_NAMES} is enabled - even on the very
 * first table access for that schema, before any other access has happened to prime the schema
 * cache with a matching case.
 *
 * @author Jeff Jensen
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class PostgresqlUppercaseSchemaIT
{
    private static final String SCHEMA = "core";
    private static final String TABLE = "user_account";

    private IDatabaseConnection _connection;

    @BeforeEach
    protected void setUp() throws Exception
    {
        _connection = DatabaseEnvironment.getInstance().getConnection();
        final Statement statement = _connection.getConnection().createStatement();
        statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE;");
        statement.execute("CREATE SCHEMA " + SCHEMA + ";");
        statement.execute("CREATE TABLE " + SCHEMA + "." + TABLE
                + "(id INTEGER NOT NULL, username VARCHAR(32));");
        statement.close();
        // Re-acquire the connection so the new schema is visible - mirrors the
        // same re-connect PostgresqlUuidIT needs after DDL.
        _connection.close();
        _connection = DatabaseEnvironment.getInstance().getConnection();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        if (!Objects.isNull(_connection))
        {
            final Statement statement = _connection.getConnection().createStatement();
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE;");
            statement.close();
            _connection.close();
            _connection = null;
        }
    }

    @Test
    void testCleanInsert_withUppercaseSchemaAgainstLowercasePostgresSchema_insertsSuccessfully()
            throws Exception
    {
        _connection.getConfig().setFeature(
                DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, true);

        final String qualifiedTag =
                SCHEMA.toUpperCase() + "." + TABLE.toUpperCase();
        final String xml = "<?xml version=\"1.0\"?><dataset>"
                + "<" + qualifiedTag + " ID=\"10\" USERNAME=\"john\"/>"
                + "<" + qualifiedTag + " ID=\"11\" USERNAME=\"josh\"/>"
                + "</dataset>";

        final IDataSet dataSet = new FlatXmlDataSetBuilder()
                .build(new InputSource(new StringReader(xml)));

        DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);

        final ITable table =
                _connection.createDataSet().getTable(SCHEMA + "." + TABLE);
        assertThat(table.getRowCount()).as("row count.").isEqualTo(2);
        assertThat(table.getValue(0, "username")).as("row 0 username.")
                .isEqualTo("john");
        assertThat(table.getValue(1, "username")).as("row 1 username.")
                .isEqualTo("josh");
    }
}
