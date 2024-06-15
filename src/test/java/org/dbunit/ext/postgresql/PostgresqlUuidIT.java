package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.xml.sax.InputSource;

/**
 * @author Edward Mann <e.dev@edmann.com>
 *         <p>
 *         Created: May 27, 2024
 */
@EnabledIfEnvironmentVariable(named = "MAVEN_CMD_LINE_ARGS", matches = "(.*)postgresql(.*)")
class PostgresqlUuidIT
{

    private IDatabaseConnection _connection;
    private final String testTable = "t2";
    // @formatter:off
    private static final String xmlData = "<?xml version=\"1.0\"?>" +
            "<dataset>" +
            "<T2 ID=\"08004327-3f6c-4335-9738-0b2bf885cc43\" />" +
            "<T2 ID=\"1a0e342a-02cc-43bf-9643-f9ea312dd349\" />" +
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
        stat.execute("CREATE TABLE " + testTable + "(ID uuid NOT NULL);");
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
    void xtestUUidDataType() throws Exception
    {

        assertThat(_connection).as("didn't get a connection").isNotNull();
        final DatabaseConfig config = _connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new PostgresqlDataTypeFactory());

        try
        {
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
                        .isEqualTo(Types.OTHER);
                assertThat(col.getSqlTypeName()).isEqualTo("uuid");
            }

            DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);
            ids = _connection.createDataSet();
            final ITable it = ids.getTable(testTable);
            assertThat(it.getValue(0, "ID")).isNotNull()
                    .isEqualTo("08004327-3f6c-4335-9738-0b2bf885cc43");
            assertThat(it.getValue(1, "ID"))
                    .isEqualTo("1a0e342a-02cc-43bf-9643-f9ea312dd349");
        } catch (final Exception e)
        {
            assertEquals("DatabaseOperation.CLEAN_INSERT... no exception",
                    "" + e);
        }
    }

}
