package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.sql.Statement;
import java.sql.Types;
import java.util.Objects;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xml.sax.InputSource;

@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class PostgresSQLOidIT
{
    private IDatabaseConnection _connection;
    private final String testTable = "t2";
    // @formatter:off
    private static final String xmlData = "<?xml version=\"1.0\"?>" +
            "<dataset>" +
            "<T2 DATA=\"[NULL]\" />" +
            "<T2 DATA=\"\\[text UTF-8](Anything)\" />" +
            "</dataset>";
    // @formatter:on

    @BeforeEach
    protected void setUp() throws Exception
    {
        // Load active postgreSQL profile and connection from Maven pom.xml.
        _connection = DatabaseEnvironment.getInstance().getConnection();
        final Statement stat = _connection.getConnection().createStatement();
        // DELETE SQL OID tables
        stat.execute("DROP TABLE IF EXISTS " + testTable + ";");

        // Create SQL OID tables
        stat.execute("CREATE TABLE " + testTable + "(DATA OID);");
        stat.close();
        // TODO found that if close the connection and create again the t2 table
        // will be there for test. There must be something i'm missing on this.
        _connection.close();
        _connection = DatabaseEnvironment.getInstance().getConnection();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        if (!Objects.isNull(_connection))
        {
            final Statement stat =
                    _connection.getConnection().createStatement();
            // DELETE SQL OID tables
            stat.execute("DROP TABLE IF EXISTS " + testTable + ";");
            _connection.close();

            _connection = null;
        }
    }

    @Test
    void testOidDataType_withNullAndBinaryValues_roundTripsThroughDatabase() throws Exception
    {
        assertThat(_connection).as("didn't get a connection").isNotNull();
        final DatabaseConfig config = _connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new PostgresqlDataTypeFactory());

        final ReplacementDataSet dataSet =
                new ReplacementDataSet(new FlatXmlDataSetBuilder()
                        .build(new InputSource(new StringReader(xmlData))));
        dataSet.addReplacementObject("[NULL]", null);
        dataSet.setStrictReplacement(true);

        IDataSet ids;
        ids = _connection.createDataSet();
        final ITableMetaData itmd = ids.getTableMetaData(testTable);
        final Column[] cols = itmd.getColumns();
        ids = _connection.createDataSet();
        for (final Column col : cols)
        {
            assertThat(col.getDataType().getSqlType())
                    .isEqualTo(Types.BIGINT);
            assertThat(col.getSqlTypeName()).isEqualTo("oid");
        }

        DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);
        ids = _connection.createDataSet();
        final ITable it = ids.getTable(testTable);
        assertThat(it.getValue(0, "DATA")).isNull();
        assertThat("\\[text UTF-8](Anything)".getBytes())
                .isEqualTo(it.getValue(1, "DATA"));
    }

    /**
     * Issue 693: a PostgreSQL oid column is a generic object identifier, not necessarily a large
     * object reference (see https://www.postgresql.org/docs/current/datatype-oid.html), e.g. a
     * real catalog object's own oid such as a table's oid via <code>'table'::regclass</code>,
     * the original bug report's own example. Reading such a row must not fail the whole read,
     * and the connection must remain usable for subsequent rows afterward.
     */
    @Test
    void testOidDataType_withOidNotReferencingALargeObject_readsAsNullInsteadOfFailing()
            throws Exception
    {
        assertThat(_connection).as("didn't get a connection").isNotNull();
        final DatabaseConfig config = _connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new PostgresqlDataTypeFactory());

        // dbUnit's own write path (setSqlValue()) always creates a genuine large object, so use
        // raw SQL - two distinct real catalog oids - to put non-large-object values into the
        // column.
        try (Statement stat = _connection.getConnection().createStatement())
        {
            stat.execute("INSERT INTO " + testTable + "(DATA) VALUES ('pg_class'::regclass::oid)");
            stat.execute("INSERT INTO " + testTable + "(DATA) VALUES ('pg_proc'::regclass::oid)");
        }

        final IDataSet ids = _connection.createDataSet();
        final ITable it = ids.getTable(testTable);

        assertThat(it.getRowCount()).isEqualTo(2);
        assertThat(it.getValue(0, "DATA"))
                .as("a non-large-object oid should read as null instead of throwing.")
                .isNull();
        assertThat(it.getValue(1, "DATA"))
                .as("reading a second, distinct non-large-object oid afterward should still "
                        + "work, proving the connection recovered from the first failed read.")
                .isNull();
    }
}
