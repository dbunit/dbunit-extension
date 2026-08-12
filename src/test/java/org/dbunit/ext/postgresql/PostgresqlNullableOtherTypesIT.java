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
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xml.sax.InputSource;

/**
 * Integration test proving {@link UuidType}, {@link InetType}, and
 * {@link CitextType} all bind a real sql {@code NULL} for a null column
 * value via {@code CLEAN_INSERT}, instead of throwing
 * {@link NullPointerException} from their PGobject-building helper (issues
 * 677 and 930).
 *
 * <p>{@link GenericEnumType} shares the same fix (see its unit tests) but is
 * deliberately not exercised here: a custom PostgreSQL enum column is
 * reported as sql type {@code VARCHAR} (not {@code OTHER}) by
 * {@code DatabaseMetaData.getColumns()}, so {@link PostgresqlDataTypeFactory}
 * never selects {@code GenericEnumType} for a real table column in the first
 * place - a separate, pre-existing defect unrelated to null handling
 * (issue 933), discovered while writing this test.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 */
@EnabledIfSystemProperty(named = "dbunit.profile", matches = "postgresql")
class PostgresqlNullableOtherTypesIT
{
    private IDatabaseConnection _connection;
    private final String testTable = "nullable_other_types_test";
    // @formatter:off
    private static final String xmlData = "<?xml version=\"1.0\"?>" +
            "<dataset>" +
            "<NULLABLE_OTHER_TYPES_TEST ID=\"1\" UID=\"08004327-3f6c-4335-9738-0b2bf885cc43\" IP=\"192.168.1.1\" NOTE=\"Hello\" />" +
            "<NULLABLE_OTHER_TYPES_TEST ID=\"2\" UID=\"[NULL]\" IP=\"[NULL]\" NOTE=\"[NULL]\" />" +
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
            stat.execute("CREATE EXTENSION IF NOT EXISTS citext;");
            stat.execute("CREATE TABLE " + testTable
                    + "(ID INTEGER NOT NULL, UID uuid, IP inet, NOTE citext);");
        }
        // Mirrors PostgresqlUuidIT/PostgresqlJsonIT: the table isn't visible
        // to a fresh dataset without reopening the connection.
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
    void testCleanInsert_withNullUuidInetCitextValues_roundTripsNullWithoutThrowing()
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

        DatabaseOperation.CLEAN_INSERT.execute(_connection, dataSet);

        final IDataSet actualDataSet = _connection.createDataSet();
        final ITable actualTable = actualDataSet.getTable(testTable);

        assertThat(actualTable.getValue(0, "UID")).as("row 0 UID.")
                .isEqualTo("08004327-3f6c-4335-9738-0b2bf885cc43");
        assertThat(actualTable.getValue(0, "IP")).as("row 0 IP.")
                .isEqualTo("192.168.1.1");
        assertThat(actualTable.getValue(0, "NOTE")).as("row 0 NOTE.")
                .isEqualTo("Hello");

        assertThat(actualTable.getValue(1, "UID"))
                .as("row 1 UID should be null.").isNull();
        assertThat(actualTable.getValue(1, "IP"))
                .as("row 1 IP should be null.").isNull();
        assertThat(actualTable.getValue(1, "NOTE"))
                .as("row 1 NOTE should be null.").isNull();
    }
}
