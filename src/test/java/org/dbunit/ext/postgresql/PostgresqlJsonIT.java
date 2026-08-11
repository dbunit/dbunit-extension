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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration test proving {@link JsonType} round-trips PostgreSQL
 * {@code json}/{@code jsonb} column values, including a null value, through
 * a real database (issue 574).
 *
 * @author Jeff Jensen
 * @since 3.4.1
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class PostgresqlJsonIT
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private IDatabaseConnection _connection;
    private final String testTable = "json_test";
    // @formatter:off
    private static final String xmlData = "<?xml version=\"1.0\"?>" +
            "<dataset>" +
            "<JSON_TEST ID=\"1\" DATA=\"{&quot;a&quot;:1}\" DATA_B=\"{&quot;b&quot;:2}\" />" +
            "<JSON_TEST ID=\"2\" DATA=\"[NULL]\" DATA_B=\"[NULL]\" />" +
            "</dataset>";
    // @formatter:on

    @BeforeEach
    protected void setUp() throws Exception
    {
        // Load active postgreSQL profile and connection from Maven pom.xml.
        _connection = DatabaseEnvironment.getInstance().getConnection();
        try (Statement stat = _connection.getConnection().createStatement())
        {
            stat.execute("DROP TABLE IF EXISTS " + testTable + ";");
            stat.execute("CREATE TABLE " + testTable
                    + "(ID INTEGER NOT NULL, DATA json, DATA_B jsonb);");
        }
        // Mirrors PostgresqlUuidIT: the table isn't visible to a fresh
        // dataset without reopening the connection.
        _connection.close();
        _connection = DatabaseEnvironment.getInstance().getConnection();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        if (!Objects.isNull(_connection))
        {
            try (Statement stat =
                    _connection.getConnection().createStatement())
            {
                stat.execute("DROP TABLE IF EXISTS " + testTable + ";");
            } finally
            {
                _connection.close();
                _connection = null;
            }
        }
    }

    @Test
    void testJsonDataType_withJsonAndJsonbColumns_roundTripsThroughDatabase()
            throws Exception
    {
        assertThat(_connection).as("didn't get a connection.").isNotNull();
        final DatabaseConfig config = _connection.getConfig();
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new PostgresqlDataTypeFactory());

        final ReplacementDataSet dataSet =
                new ReplacementDataSet(new FlatXmlDataSetBuilder()
                        .build(new InputSource(new StringReader(xmlData))));
        dataSet.addReplacementObject("[NULL]", null);
        dataSet.setStrictReplacement(true);

        final IDataSet metaDataSet = _connection.createDataSet();
        final ITableMetaData itmd = metaDataSet.getTableMetaData(testTable);
        boolean dataChecked = false;
        boolean dataBChecked = false;
        for (final Column col : itmd.getColumns())
        {
            if ("DATA".equalsIgnoreCase(col.getColumnName()))
            {
                dataChecked = true;
                assertThat(col.getDataType().getSqlType())
                        .as("DATA column sql type.").isEqualTo(Types.OTHER);
                assertThat(col.getSqlTypeName()).as("DATA column sql type name.")
                        .isEqualTo("json");
            } else if ("DATA_B".equalsIgnoreCase(col.getColumnName()))
            {
                dataBChecked = true;
                assertThat(col.getDataType().getSqlType())
                        .as("DATA_B column sql type.").isEqualTo(Types.OTHER);
                assertThat(col.getSqlTypeName()).as("DATA_B column sql type name.")
                        .isEqualTo("jsonb");
            }
        }
        assertThat(dataChecked).as("The DATA column should be present in the metadata.")
                .isTrue();
        assertThat(dataBChecked).as("The DATA_B column should be present in the metadata.")
                .isTrue();

        DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);

        final IDataSet actualDataSet = _connection.createDataSet();
        final ITable actualTable = actualDataSet.getTable(testTable);

        // PostgreSQL stores "json" verbatim but reformats "jsonb" on write
        // (e.g. inserted whitespace), so compare parsed document structure
        // rather than raw text.
        assertThat(OBJECT_MAPPER.readTree(
                String.valueOf(actualTable.getValue(0, "DATA"))))
                        .as("DATA (json) row 0 should round-trip the same document.")
                        .isEqualTo(OBJECT_MAPPER.readTree("{\"a\":1}"));
        assertThat(OBJECT_MAPPER.readTree(
                String.valueOf(actualTable.getValue(0, "DATA_B"))))
                        .as("DATA_B (jsonb) row 0 should round-trip the same document.")
                        .isEqualTo(OBJECT_MAPPER.readTree("{\"b\":2}"));

        assertThat(actualTable.getValue(1, "DATA"))
                .as("DATA (json) row 1 should be null.").isNull();
        assertThat(actualTable.getValue(1, "DATA_B"))
                .as("DATA_B (jsonb) row 1 should be null.").isNull();
    }
}
