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

/**
 * Integration test proving {@link ArrayType} round-trips PostgreSQL array
 * column values - including a whole-column null, a null element, an empty
 * array, and an element requiring quoting - through a real database (issue
 * 646).
 *
 * @author Jeff Jensen
 * @since 3.4.1
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class PostgresqlArrayIT
{
    private IDatabaseConnection _connection;
    private final String testTable = "array_test";
    // @formatter:off
    private static final String xmlData = "<?xml version=\"1.0\"?>" +
            "<dataset>" +
            "<ARRAY_TEST ID=\"1\" NUMS=\"{1,2,3}\" TAGS=\"{&quot;a&quot;,&quot;b,c&quot;}\" />" +
            "<ARRAY_TEST ID=\"2\" NUMS=\"[NULL]\" TAGS=\"[NULL]\" />" +
            "<ARRAY_TEST ID=\"3\" NUMS=\"{1,NULL,3}\" TAGS=\"{}\" />" +
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
                    + "(ID INTEGER NOT NULL, NUMS integer[], TAGS text[]);");
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
    void testArrayDataType_withIntegerAndTextArrayColumns_roundTripsThroughDatabase()
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
        boolean numsChecked = false;
        boolean tagsChecked = false;
        for (final Column col : itmd.getColumns())
        {
            if ("NUMS".equalsIgnoreCase(col.getColumnName()))
            {
                numsChecked = true;
                assertThat(col.getDataType().getSqlType())
                        .as("NUMS column sql type.").isEqualTo(Types.ARRAY);
                assertThat(col.getSqlTypeName()).as("NUMS column sql type name.")
                        .isEqualTo("_int4");
            } else if ("TAGS".equalsIgnoreCase(col.getColumnName()))
            {
                tagsChecked = true;
                assertThat(col.getDataType().getSqlType())
                        .as("TAGS column sql type.").isEqualTo(Types.ARRAY);
                assertThat(col.getSqlTypeName()).as("TAGS column sql type name.")
                        .isEqualTo("_text");
            }
        }
        assertThat(numsChecked).as("The NUMS column should be present in the metadata.")
                .isTrue();
        assertThat(tagsChecked).as("The TAGS column should be present in the metadata.")
                .isTrue();

        DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);

        // A plain createDataSet().getTable() issues no ORDER BY, so row
        // order is not guaranteed; order explicitly since the assertions
        // below index rows by their insertion order.
        final ITable actualTable = _connection.createQueryTable(testTable,
                "SELECT * FROM " + testTable + " ORDER BY ID");

        assertThat(actualTable.getValue(0, "NUMS"))
                .as("Row 0 NUMS should round-trip its integer elements.")
                .isEqualTo("{1,2,3}");
        assertThat(actualTable.getValue(0, "TAGS"))
                .as("Row 0 TAGS should round-trip, quoting only the element "
                        + "containing the delimiter.")
                .isEqualTo("{a,\"b,c\"}");

        assertThat(actualTable.getValue(1, "NUMS"))
                .as("Row 1 NUMS should be null.").isNull();
        assertThat(actualTable.getValue(1, "TAGS"))
                .as("Row 1 TAGS should be null.").isNull();

        assertThat(actualTable.getValue(2, "NUMS"))
                .as("Row 2 NUMS should round-trip its null element.")
                .isEqualTo("{1,NULL,3}");
        assertThat(actualTable.getValue(2, "TAGS"))
                .as("Row 2 TAGS should round-trip as an empty array.")
                .isEqualTo("{}");
    }
}
