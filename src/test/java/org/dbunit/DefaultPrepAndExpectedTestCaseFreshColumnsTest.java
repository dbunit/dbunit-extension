package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Tests that {@link DefaultPrepAndExpectedTestCase} checks the column names of a
 * table's verification against the columns the comparison reads from the
 * database, in H2: a column of a type the default data type factory does not
 * know, and a column added after the set up read the table, count; a column
 * comparer that the include or exclude column names leave out is reported.
 *
 * @since 3.6.0
 */
class DefaultPrepAndExpectedTestCaseFreshColumnsTest
{
    private static final String PREP_U = "<dataset><U/></dataset>";
    private static final String EXPECTED_U_TWO_ROWS_IN_REVERSE = "<dataset>"
            + "<U ID='2' IV='INTERVAL &apos;3&apos; DAY'/>"
            + "<U ID='1' IV='INTERVAL &apos;2&apos; DAY'/></dataset>";
    private static final String INSERT_U_TWO_ROWS = "INSERT INTO U (ID, IV)"
            + " VALUES (1, INTERVAL '2' DAY), (2, INTERVAL '3' DAY)";
    private static final String INSERT_U_CHANGED_ROW = "INSERT INTO U (ID, IV)"
            + " VALUES (1, INTERVAL '2' DAY), (2, INTERVAL '4' DAY)";
    private static final String PREP_S = "<dataset><S ID='1'/><S ID='2'/></dataset>";
    private static final String EXPECTED_S_TWO_ROWS_IN_REVERSE = "<dataset>"
            + "<S ID='2' NAME='x'/><S ID='1' NAME='x'/></dataset>";
    private static final String ADD_NAME_TO_S =
            "ALTER TABLE S ADD COLUMN NAME VARCHAR(20) DEFAULT 'x'";
    private static final String PREP_T = "<dataset><T ID='1' NAME='a'/></dataset>";

    private IDatabaseConnection connection;
    private DefaultPrepAndExpectedTestCase testCase;

    @BeforeEach
    void setUp() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        try (Statement statement = connection.getConnection().createStatement())
        {
            statement.execute(
                    "CREATE TABLE U (ID INT PRIMARY KEY, IV INTERVAL DAY, G GEOMETRY)");
            statement.execute("CREATE TABLE S (ID INT PRIMARY KEY)");
            statement.execute("CREATE TABLE T (ID INT PRIMARY KEY, NAME VARCHAR(20))");
        }
        final IDatabaseTester databaseTester = new DefaultDatabaseTester(connection);
        testCase = new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(),
                databaseTester);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        connection.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false})
    void testVerifyData_includeOfColumnOfUnknownType_passesAndComparesTheColumn(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final VerifyTableDefinition definition = definition("U",
                new String[] {}, new String[] {"ID", "IV"}, null, sortOnFilteredColumnsOnly);

        assertThatCode(() -> verify(PREP_U, EXPECTED_U_TWO_ROWS_IN_REVERSE, definition,
                INSERT_U_TWO_ROWS))
                        .as("An include name of a column of a type the data type factory does not"
                                + " know names a column of the table.")
                        .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVerifyData_includeOfColumnOfUnknownTypeWithChangedValue_failsTheComparison(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final VerifyTableDefinition definition = definition("U",
                new String[] {}, new String[] {"ID", "IV"}, null, sortOnFilteredColumnsOnly);

        assertThatThrownBy(() -> verify(PREP_U, EXPECTED_U_TWO_ROWS_IN_REVERSE, definition,
                INSERT_U_CHANGED_ROW))
                        .as("The column of the unknown type is compared, so its changed value"
                                + " fails.")
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("IV");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false})
    void testVerifyData_columnComparerOfColumnOfUnknownType_isUsed(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("IV", ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("U",
                new String[] {"G"}, new String[] {}, comparers, sortOnFilteredColumnsOnly);

        assertThatCode(() -> verify(PREP_U, EXPECTED_U_TWO_ROWS_IN_REVERSE, definition,
                INSERT_U_TWO_ROWS))
                        .as("A comparer for a column of a type the data type factory does not"
                                + " know names a column of the table.")
                        .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false})
    void testVerifyData_includeOfColumnTheStepAdds_passesAndComparesTheColumn(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final VerifyTableDefinition definition = definition("S",
                new String[] {}, new String[] {"ID", "NAME"}, null, sortOnFilteredColumnsOnly);

        assertThatCode(() -> verify(PREP_S, EXPECTED_S_TWO_ROWS_IN_REVERSE, definition,
                ADD_NAME_TO_S))
                        .as("A column that the test step adds is a column of the table the"
                                + " comparison reads, although the set up read the table"
                                + " before.")
                        .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false})
    void testVerifyData_columnComparerOfColumnTheStepAdds_isUsed(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("NAME", ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("S",
                new String[] {}, new String[] {}, comparers, sortOnFilteredColumnsOnly);

        assertThatCode(() -> verify(PREP_S, EXPECTED_S_TWO_ROWS_IN_REVERSE, definition,
                ADD_NAME_TO_S))
                        .as("A comparer for a column that the test step adds names a column of"
                                + " the table.")
                        .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVerifyData_includeOfMisspelledColumnOnTableWithColumnOfUnknownType_failsListingEveryColumn(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final VerifyTableDefinition definition = definition("U",
                new String[] {}, new String[] {"ID", "IVV"}, null, sortOnFilteredColumnsOnly);

        assertThatThrownBy(() -> verify(PREP_U, EXPECTED_U_TWO_ROWS_IN_REVERSE, definition,
                INSERT_U_TWO_ROWS))
                        .as("A misspelled include name still fails, and the failure lists the"
                                + " columns of the unknown type too.")
                        .isInstanceOf(DatabaseUnitException.class)
                        .hasMessageContaining("The column name 'IVV' to include for table U"
                                + " matches no column of the table, whose columns are ID, IV, G,");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVerifyData_columnComparerOfMisspelledColumn_fails(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("NMAE", ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("T",
                new String[] {}, new String[] {}, comparers, sortOnFilteredColumnsOnly);

        assertThatThrownBy(() -> verify(PREP_T, PREP_T, definition, null))
                .as("A comparer of a misspelled column name still fails.")
                .isInstanceOf(DatabaseUnitException.class)
                .hasMessageContaining("The column name 'NMAE' of a column comparer for table T"
                        + " matches no column of the table, whose columns are ID, NAME,");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVerifyData_columnComparerWithNullKey_isIgnored(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put(null, ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("T",
                new String[] {}, new String[] {}, comparers, sortOnFilteredColumnsOnly);

        assertThatCode(() -> verify(PREP_T, PREP_T, definition, null))
                .as("A null key names no column, as the comparison leaves it out.")
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVerifyData_columnComparerOfColumnTheIncludeNamesLeaveOut_failsNamingTheNames(
            final boolean sortOnFilteredColumnsOnly) throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("NAME", ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("T",
                new String[] {}, new String[] {"ID"}, comparers, sortOnFilteredColumnsOnly);

        assertThatThrownBy(() -> verify(PREP_T, PREP_T, definition, null))
                .as("A comparer for a column that the include names leave out is not used.")
                .isInstanceOf(DatabaseUnitException.class)
                .hasMessage("The column comparer for column 'NAME' of table T is left out of"
                        + " the comparison by the include names [ID], so the comparer would"
                        + " not be used. Add the column to the include names or remove its"
                        + " comparer in the VerifyTableDefinition, @DbUnitVerifyTable or"
                        + " @DbUnitColumnComparer, or set the feature"
                        + " http://www.dbunit.org/features/failOnUnknownVerifyColumnName to"
                        + " false to log a warning instead of failing.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "N*", "NAM?"})
    void testVerifyData_columnComparerOfColumnTheExcludeNamesLeaveOutInAnotherWayThanExactly_fails(
            final String excludeName) throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("NAME", ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("T",
                new String[] {excludeName}, new String[] {}, comparers, false);

        assertThatThrownBy(() -> verify(PREP_T, PREP_T, definition, null))
                .as("The constructor check matches the excluded name exactly, so the"
                        + " verification must report a comparer that an exclude name leaves"
                        + " out by case or by pattern.")
                .isInstanceOf(DatabaseUnitException.class)
                .hasMessageContaining("The column comparer for column 'NAME' of table T is left"
                        + " out of the comparison by the exclude names [" + excludeName + "],");
    }

    @Test
    void testVerifyData_columnComparerOfColumnTheIncludeNamesLeaveOutAndFeatureFalse_warnsAndVerifies()
            throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("NAME", ValueComparers.isActualEqualToExpected);
        final VerifyTableDefinition definition = definition("T",
                new String[] {}, new String[] {"ID"}, comparers, false);
        connection.getConfig().setFeature(
                DatabaseConfig.FEATURE_FAIL_ON_UNKNOWN_VERIFY_COLUMN_NAME, false);
        final Logger logger = (Logger) org.slf4j.LoggerFactory
                .getLogger(VerifyColumnNamesCheck.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try
        {
            assertThatCode(() -> verify(PREP_T, PREP_T, definition, null))
                    .as("The feature set to false logs a warning instead of failing.")
                    .doesNotThrowAnyException();

            final List<String> warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
            assertThat(warnings)
                    .as("The comparer that is not used is logged as one warning.")
                    .containsExactly("The column comparer for column 'NAME' of table T is left"
                            + " out of the comparison by the include names [ID], so the"
                            + " comparer would not be used. Add the column to the include"
                            + " names or remove its comparer in the VerifyTableDefinition,"
                            + " @DbUnitVerifyTable or @DbUnitColumnComparer.");
        } finally
        {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testVerifyData_columnComparerOfColumnNeitherFilterLeavesOut_isUsed() throws Exception
    {
        final Map<String, ValueComparer> comparers = new HashMap<>();
        comparers.put("name", new AlwaysFailingComparer());
        final VerifyTableDefinition definition = definition("T",
                new String[] {}, new String[] {"ID", "NAME"}, comparers, false);

        assertThatThrownBy(() -> verify(PREP_T, PREP_T, definition, null))
                .as("The comparer of a column that is compared is used, in any case.")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ALWAYS_FAILS");
    }

    private static VerifyTableDefinition definition(final String tableName,
            final String[] excludeColumns, final String[] includeColumns,
            final Map<String, ValueComparer> comparers,
            final boolean sortOnFilteredColumnsOnly)
    {
        return new VerifyTableDefinition(tableName, excludeColumns, includeColumns, null,
                comparers, sortOnFilteredColumnsOnly);
    }

    private void verify(final String prepXml, final String expectedXml,
            final VerifyTableDefinition definition, final String stepSql) throws Exception
    {
        testCase.setPrepDs(dataSet(prepXml));
        testCase.setExpectedDs(dataSet(expectedXml));
        testCase.setVerifyTableDefs(new VerifyTableDefinition[] {definition});
        testCase.setupData();
        if (stepSql != null)
        {
            final Connection jdbcConnection = connection.getConnection();
            try (Statement statement = jdbcConnection.createStatement())
            {
                statement.execute(stepSql);
            }
        }

        testCase.verifyData();
    }

    private static IDataSet dataSet(final String xml) throws Exception
    {
        return new FlatXmlDataSetBuilder().build(new StringReader(xml));
    }

    private static class AlwaysFailingComparer implements ValueComparer
    {
        @Override
        public String compare(final ITable expectedTable, final ITable actualTable,
                final int rowNum, final String columnName, final DataType dataType,
                final Object expectedValue, final Object actualValue)
        {
            return "ALWAYS_FAILS used on " + columnName;
        }
    }
}
