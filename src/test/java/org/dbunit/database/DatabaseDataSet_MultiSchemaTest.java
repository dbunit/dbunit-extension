package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.dbunit.DdlExecutor;
import org.dbunit.H2Environment;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test the multiple schema support of DatabaseDataSet.
 *
 * <p>
 * This test case uses the H2 database because it offers easy handling of
 * schemas / users.
 * </p>
 */
public class DatabaseDataSet_MultiSchemaTest
{
    private static final String DATABASE = "multischematest";
    private static final String USERNAME_ADMIN = "sa";
    private static final String USERNAME_DBUNIT = "DBUNITUSER";
    private static final String USERNAME_DEFAULT = "DEFAULTUSER";
    private static final String PASSWORD = "test";
    private static final String PASSWORD_NONE = "";
    private static final String SCHEMA_DEFAULT = USERNAME_DEFAULT;
    private static final String SCHEMA_DBUNIT = USERNAME_DBUNIT;
    private static final String SCHEMA_NONE = null;

    private static final String TABLE_BAR = "BAR";
    private static final String TABLE_FOO = "FOO";

    private static final String TABLE_BAR_IN_SCHEMA_DBUNIT =
            SCHEMA_DBUNIT + "." + TABLE_BAR;
    private static final String TABLE_FOO_IN_SCHEMA_DEFAULT =
            SCHEMA_DEFAULT + "." + TABLE_FOO;

    private static final Boolean IS_USING_QUALIFIED_TABLE_NAMES = Boolean.TRUE;
    private static final Boolean IS_NOT_USING_QUALIFIED_TABLE_NAMES =
            Boolean.FALSE;

    private static final String SETUP_DDL_FILE =
            "sql/h2_multischema_permission_test.sql";

    private static Connection connectionDdl;

    private IDatabaseConnection connectionTest;

    private final TestMetadataHandler testMetadataHandler =
            new TestMetadataHandler();

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        // create database and schemas for tests
        connectionDdl = H2Environment.createJdbcConnection(DATABASE);
        DdlExecutor.executeDdlFile(TestUtils.getFile(SETUP_DDL_FILE),
                connectionDdl);
    }

    @AfterAll
    public static void tearDownClass() throws Exception
    {
        // close connection after all tests so schemas stay around
        if (connectionDdl != null && !connectionDdl.isClosed())
        {
            connectionDdl.close();
        }
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        if (connectionTest != null)
        {
            connectionTest.close();
        }

        testMetadataHandler.clearSchemaSet();
    }

    /**
     * Admin user has full access to all tables in all schemas.
     *
     * @throws Exception
     */
    @Test
    void testPermissions_AdminUser_QualifiedTableNames() throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_ADMIN,
                PASSWORD_NONE, SCHEMA_NONE, IS_USING_QUALIFIED_TABLE_NAMES);

        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(2);
        assertThat(allTables[0]).isEqualTo(TABLE_BAR_IN_SCHEMA_DBUNIT);
        assertThat(allTables[1]).isEqualTo(TABLE_FOO_IN_SCHEMA_DEFAULT);
    }

    /**
     * As basic schema owner you will have access to your own tables, but not to
     * other ones.
     *
     * @throws Exception
     */
    @Test
    void testPermissions_OwningUser_QualifiedTableNames() throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_DEFAULT,
                PASSWORD, SCHEMA_DEFAULT, IS_USING_QUALIFIED_TABLE_NAMES);

        // Own table
        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(1);
        assertThat(allTables[0]).isEqualTo(TABLE_FOO_IN_SCHEMA_DEFAULT);

        // Table of other user/schema
        assertThrows(DataSetException.class,
                () -> dataSet.getTable(TABLE_BAR_IN_SCHEMA_DBUNIT));
    }

    /**
     * If we don't use qualified table names, then we still use only our own
     * tables.
     *
     * @throws Exception
     */
    @Test
    void testPermissions_OwningUser_UnqualifiedTableNames() throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_DEFAULT,
                PASSWORD, SCHEMA_DEFAULT, IS_NOT_USING_QUALIFIED_TABLE_NAMES);

        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(1);
        assertThat(allTables[0]).isEqualTo(TABLE_FOO);

        // Table of other user/schema
        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTable(TABLE_BAR));
    }

    /**
     * A special dbunit user could be allowed to access tables from other users
     * to prepare test data.
     *
     * @throws Exception
     */
    @Test
    // THIS ONE FAILS WITHOUT ISSUE 368 IN PLACE
    void testPermissions_DbunitUser_QualifiedTables() throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_DBUNIT,
                PASSWORD, SCHEMA_DBUNIT, IS_USING_QUALIFIED_TABLE_NAMES);

        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(1);
        assertThat(allTables[0]).isEqualTo(TABLE_BAR_IN_SCHEMA_DBUNIT);

        // Access table of other owner - metadata will be lazy loaded
        final ITable table = dataSet.getTable(TABLE_FOO_IN_SCHEMA_DEFAULT);
        assertThat(table).isNotNull();

        // Unqualified access to table isn't possible
        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTable(TABLE_FOO));
    }

    @Test
    void testPermissions_DbunitUser_UnqualifiedTables() throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_DBUNIT,
                PASSWORD, SCHEMA_DBUNIT, IS_NOT_USING_QUALIFIED_TABLE_NAMES);

        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(1);
        assertThat(allTables[0]).isEqualTo(TABLE_BAR);

        // Access table of other owner
        final ITable table = dataSet.getTable(TABLE_BAR);
        assertThat(table).isNotNull();

        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTable(TABLE_FOO));
    }

    /**
     * Without explicit schema selection, all available tables will be loaded...
     *
     * @throws Exception
     */
    @Test
    void testPermissions_DbunitUser_QualifiedTableNames_NoSpecifiedSchema()
            throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_DBUNIT,
                PASSWORD, SCHEMA_NONE, IS_USING_QUALIFIED_TABLE_NAMES);

        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(2);
        assertThat(allTables[0]).isEqualTo(TABLE_BAR_IN_SCHEMA_DBUNIT);
        assertThat(allTables[1]).isEqualTo(TABLE_FOO_IN_SCHEMA_DEFAULT);

        // Qualified access to own tables...
        ITable table = dataSet.getTable(TABLE_BAR_IN_SCHEMA_DBUNIT);
        assertThat(table).isNotNull();

        // Qualified access to other tables...
        table = dataSet.getTable(TABLE_FOO_IN_SCHEMA_DEFAULT);
        assertThat(table).isNotNull();

        // But unqualified access doesn't work...
        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTable(TABLE_FOO));
    }

    /**
     * Without explizit schema selection, all available tables will be loaded -
     * but without qualified table access, no metadata will be found.
     *
     * @throws Exception
     */
    @Test
    void testPermissions_DbunitUser_UnqualifiedTableNames_NoSpecifiedSchema()
            throws Exception
    {
        final IDataSet dataSet = makeDataSet(DATABASE, USERNAME_DBUNIT,
                PASSWORD, SCHEMA_NONE, IS_NOT_USING_QUALIFIED_TABLE_NAMES);

        final String[] allTables = dataSet.getTableNames();
        Arrays.sort(allTables);
        assertThat(allTables).hasSize(2);
        assertThat(allTables[0]).isEqualTo(TABLE_BAR);
        assertThat(allTables[1]).isEqualTo(TABLE_FOO);

        // Qualified access to own tables...
        assertThrows(DataSetException.class, () -> dataSet.getTable(TABLE_BAR));

        // Qualified access to other tables...
        assertThrows(DataSetException.class, () -> dataSet.getTable(TABLE_FOO));

        // But unqualified access doesn't work...
        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTable(TABLE_FOO_IN_SCHEMA_DEFAULT));
    }

    @Test
    void testSchemaCaseSensitivity() throws Exception
    {
        final IDataSet set = makeDataSet(DATABASE, USERNAME_ADMIN,
                PASSWORD_NONE, SCHEMA_NONE, IS_USING_QUALIFIED_TABLE_NAMES);

        set.getTableMetaData(TABLE_FOO_IN_SCHEMA_DEFAULT);
        set.getTableMetaData(
                TABLE_FOO_IN_SCHEMA_DEFAULT.toLowerCase(Locale.ENGLISH));
        assertThat(testMetadataHandler.getSchemaCount()).isEqualTo(1);
    }

    private IDataSet makeDataSet(final String databaseName,
            final String username, final String password, final String schema,
            final boolean useQualifiedTableNames) throws Exception
    {
        makeDatabaseConnection(databaseName, username, password, schema,
                useQualifiedTableNames);

        return connectionTest.createDataSet();
    }

    private void makeDatabaseConnection(final String databaseName,
            final String username, final String password, final String schema,
            final boolean useQualifiedTableNames) throws Exception
    {
        final Connection jdbcConnection = H2Environment
                .createJdbcConnection(databaseName, username, password);
        connectionTest = new DatabaseConnection(jdbcConnection, schema);
        final DatabaseConfig config = connectionTest.getConfig();
        config.setProperty(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES,
                useQualifiedTableNames);
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new H2DataTypeFactory());
        config.setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER,
                testMetadataHandler);
    }

    private static class TestMetadataHandler extends DefaultMetadataHandler
    {
        private final Set<String> schemaSet = new HashSet<>();

        @Override
        public ResultSet getTables(final DatabaseMetaData metaData,
                final String schemaName, final String[] tableType)
                throws SQLException
        {
            schemaSet.add(schemaName);
            return super.getTables(metaData, schemaName, tableType);
        }

        public int getSchemaCount()
        {
            return schemaSet.size();
        }

        public void clearSchemaSet()
        {
            schemaSet.clear();
        }
    }
}
