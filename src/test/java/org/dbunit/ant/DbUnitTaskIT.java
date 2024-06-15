/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2024, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.dbunit.ant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseUnitException;
import org.dbunit.IDatabaseTester;
import org.dbunit.ant.adapter.BuildFileExtension;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.ext.mssql.InsertIdentityOperation;
import org.dbunit.ext.oracle.OracleDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ant-based test class for the Dbunit ant task definition.
 *
 * @author Timothy Ruppert
 * @author Ben Cox
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Jun 10, 2002
 * @see org.dbunit.ant.AntTest
 */
public class DbUnitTaskIT
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    @RegisterExtension
    public BuildFileExtension rule = new BuildFileExtension();

    static protected Class classUnderTest = DbUnitTaskIT.class;

    private static final String BUILD_FILE_DIR = "xml";
    private static final String OUTPUT_DIR = "target/xml";

    private File outputDir;

    @BeforeAll
    public static void initializeDbEnvironment() throws Exception {
        // This line ensure test database is initialized
        DatabaseEnvironment.getInstance();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        final String filePath = BUILD_FILE_DIR + "/antTestBuildFile.xml";
        assertThat(TestUtils.getFile(filePath)).as("Buildfile not found")
        .isFile();
        rule.configureProject(TestUtils.getFileName(filePath));
        outputDir = new File(rule.getProject().getBaseDir(), OUTPUT_DIR);
        outputDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        outputDir = new File(rule.getProject().getBaseDir(), OUTPUT_DIR);
        FileHelper.deleteDirectory(outputDir);
    }

    @Test
    public void testNoDriver()
    {
        assertThrows(BuildException.class,
                () -> rule.executeTarget("no-driver"),
                "Should have required a driver attribute.");
    }

    @Test
    public void testNoDbUrl()
    {
        assertThrows(BuildException.class,
                () -> rule.executeTarget("no-db-url"),
                "Should have required a url attribute.");
    }

    @Test
    public void testNoUserid()
    {
        assertThrows(BuildException.class,
                () -> rule.executeTarget("no-userid"),
                "Should have required a userid attribute.");
    }

    @Test
    public void testNoPassword()
    {
        assertThatThrownBy(() -> rule.executeTarget("no-password"))
        .as("Should have required a password attribute.")
        .isInstanceOf(BuildException.class);
    }

    @Test
    public void testInvalidDatabaseInformation()
    {
        final Throwable thrown =
                catchThrowable(() -> rule.executeTarget("invalid-db-info"));
        assertThat(thrown.getCause()).as("Should have thrown a SQLException.")
        .isNotNull().isInstanceOf(SQLException.class);

    }

    @Test
    public void testInvalidOperationType()
    {
        final Throwable thrown =
                catchThrowable(() -> rule.executeTarget("invalid-type"));
        assertThat(thrown.getCause())
        .as("Should have thrown an IllegalArgumentException.")
        .isNotNull().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testSetFlatFalse()
    {
        final String targetName = "set-format-xml";
        final Operation operation =
                (Operation) getFirstStepFromTarget(targetName);
        assertThat(operation.getFormat()).as(
                "Operation attribute format should have been 'xml', but was: "
                        + operation.getFormat())
        .isEqualTo("xml");

    }

    @Test
    public void testResolveOperationTypes()
    {
        assertOperationType("Should have been a NONE operation",
                "test-type-none", DatabaseOperation.NONE);
        assertOperationType("Should have been an DELETE_ALL operation",
                "test-type-delete-all", DatabaseOperation.DELETE_ALL);
        assertOperationType("Should have been an INSERT operation",
                "test-type-insert", DatabaseOperation.INSERT);
        assertOperationType("Should have been an UPDATE operation",
                "test-type-update", DatabaseOperation.UPDATE);
        assertOperationType("Should have been an REFRESH operation",
                "test-type-refresh", DatabaseOperation.REFRESH);
        assertOperationType("Should have been an CLEAN_INSERT operation",
                "test-type-clean-insert", DatabaseOperation.CLEAN_INSERT);
        assertOperationType("Should have been an CLEAN_INSERT operation",
                "test-type-clean-insert-composite",
                DatabaseOperation.CLEAN_INSERT);
        assertOperationType("Should have been an CLEAN_INSERT operation",
                "test-type-clean-insert-composite-combine",
                DatabaseOperation.CLEAN_INSERT);
        assertOperationType("Should have been an DELETE operation",
                "test-type-delete", DatabaseOperation.DELETE);
        assertOperationType("Should have been an MSSQL_INSERT operation",
                "test-type-mssql-insert", InsertIdentityOperation.INSERT);
        assertOperationType("Should have been an MSSQL_REFRESH operation",
                "test-type-mssql-refresh", InsertIdentityOperation.REFRESH);
        assertOperationType("Should have been an MSSQL_CLEAN_INSERT operation",
                "test-type-mssql-clean-insert",
                InsertIdentityOperation.CLEAN_INSERT);
    }

    @Test
    public void testInvalidCompositeOperationSrc()
    {
        expectBuildException("invalid-composite-operation-src",
                "Should have objected to nested operation src attribute "
                        + "being set.");
    }

    @Test
    public void testInvalidCompositeOperationFlat()
    {
        expectBuildException("invalid-composite-operation-format-flat",
                "Should have objected to nested operation format attribute "
                        + "being set.");
    }

    @Test
    public void testExportFull()
    {
        final String targetName = "test-export-full";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("Should have been a flat format, "
                + "but was: " + export.getFormat())
        .isEqualToIgnoringCase("flat");

        final List tables = export.getTables();
        assertThat(tables)
        .as("Should have been an empty table list "
                + "(indicating a full dataset), but was: " + tables)
        .isEmpty();

    }

    @Test
    public void testExportPartial()
    {
        final String targetName = "test-export-partial";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        final List tables = export.getTables();
        assertThat(tables).as("table count").hasSize(2);

        final Table testTable = (Table) tables.get(0);
        final Table pkTable = (Table) tables.get(1);
        assertThat(testTable.getName())
        .as("Should have been been TABLE TEST_TABLE, but was: "
                + testTable.getName())
        .isEqualTo("TEST_TABLE");
        assertThat(pkTable.getName())
        .as("Should have been been TABLE PK_TABLE, but was: "
                + pkTable.getName())
        .isEqualTo("PK_TABLE");

    }

    @Test
    public void testExportWithForwardOnlyResultSetTable()
            throws SQLException, DatabaseUnitException
    {
        final String targetName =
                "test-export-forward-only-result-set-table-via-config";

        // Test if the correct result set table factory is set according to
        // dbconfig
        final Export export = (Export) getFirstStepFromTarget(targetName);
        final DbUnitTask task = getFirstTargetTask(targetName);
        final IDatabaseConnection connection = task.createConnection();
        final IDataSet dataSetToBeExported =
                export.getExportDataSet(connection);
        assertThat(connection.getConfig()
                .getProperty(
                        DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY)
                .getClass().getName()).isEqualTo("org.dbunit.database.ForwardOnlyResultSetTableFactory");

    }

    @Test
    public void testExportFlat()
    {
        final String targetName = "test-export-format-flat";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("format").isEqualTo("flat");
    }

    @Test
    public void testExportFlatWithDocytpe()
    {
        final String targetName = "test-export-format-flat-with-doctype";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("format").isEqualTo("flat");
        assertThat(export.getDoctype()).as("doctype").isEqualTo("dataset.dtd");
    }

    @Test
    public void testExportFlatWithEncoding()
    {
        final String targetName = "test-export-format-flat-with-encoding";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("format").isEqualTo("flat");
        assertThat(export.getEncoding()).as("encoding").isEqualTo("ISO-8859-1");
    }

    @Test
    public void testExportXml()
    {
        final String targetName = "test-export-format-xml";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("Should have been an xml format, "
                + "but was: " + export.getFormat())
        .isEqualToIgnoringCase("xml");
    }

    @Test
    public void testExportCsv()
    {
        final String targetName = "test-export-format-csv";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("Should have been a csv format, "
                + "but was: " + export.getFormat())
        .isEqualToIgnoringCase("csv");
    }

    @Test
    public void testExportDtd()
    {
        final String targetName = "test-export-format-dtd";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("Should have been a dtd format, "
                + "but was: " + export.getFormat())
        .isEqualToIgnoringCase("dtd");
    }

    @Test
    public void testInvalidExportFormat()
    {
        expectBuildException("invalid-export-format",
                "Should have objected to invalid format attribute.");
    }

    @Test
    public void testExportXmlOrdered() throws Exception
    {
        final String targetName = "test-export-format-xml-ordered";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.isOrdered()).as("Should be ordered").isTrue();
        assertThat(export.getFormat()).as("Should have been an xml format, "
                + "but was: " + export.getFormat()).isEqualTo("xml");

        // Test if the correct dataset is created for ordered export
        final DbUnitTask task = getFirstTargetTask(targetName);
        final IDatabaseConnection connection = task.createConnection();
        final IDataSet dataSetToBeExported =
                export.getExportDataSet(connection);
        // Ordered export should use the filtered dataset
        assertEquals(dataSetToBeExported.getClass(), FilteredDataSet.class);
    }

    @Test
    public void testExportQuery()
    {
        final String targetName = "test-export-query";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("format").isEqualTo("flat");

        final List queries = export.getTables();
        assertThat(getQueryCount(queries)).as("query count").isEqualTo(2);

        final Query testTable = (Query) queries.get(0);
        assertThat(testTable.getName()).as("name").isEqualTo("TEST_TABLE");
        assertThat(testTable.getSql()).as("sql")
        .isEqualTo("SELECT * FROM TEST_TABLE ORDER BY column0 DESC");

        final Query pkTable = (Query) queries.get(1);
        assertThat(pkTable.getName()).as("name").isEqualTo("PK_TABLE");
        assertThat(pkTable.getSql()).as("sql")
        .isEqualTo("SELECT * FROM PK_TABLE");
    }

    @Test
    public void testExportWithQuerySet()
    {
        final String targetName = "test-export-with-queryset";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("format").isEqualTo("csv");

        final List queries = export.getTables();

        assertThat(getQueryCount(queries)).as("query count").isEqualTo(1);
        assertThat(getTableCount(queries)).as("table count").isEqualTo(1);
        assertThat(getQuerySetCount(queries)).as("queryset count").isEqualTo(2);

        final Query secondTable = (Query) queries.get(0);
        assertThat(secondTable.getName()).as("name").isEqualTo("SECOND_TABLE");
        assertThat(secondTable.getSql()).as("sql")
        .isEqualTo("SELECT * FROM SECOND_TABLE");

        final QuerySet queryset1 = (QuerySet) queries.get(1);

        final Query testTable = (Query) queryset1.getQueries().get(0);

        assertThat(testTable.getName()).as("name").isEqualTo("TEST_TABLE");

        final QuerySet queryset2 = (QuerySet) queries.get(2);

        final Query pkTable = (Query) queryset2.getQueries().get(0);
        final Query testTable2 = (Query) queryset2.getQueries().get(1);

        assertThat(pkTable.getName()).as("name").isEqualTo("PK_TABLE");
        assertThat(testTable2.getName()).as("name").isEqualTo("TEST_TABLE");

        final Table emptyTable = (Table) queries.get(3);

        assertThat(emptyTable.getName()).as("name").isEqualTo("EMPTY_TABLE");
    }

    @Disabled("Ant now ignores id errors and refid is always evaluated first")
    @Test
    public void testWithBadQuerySet()
    {
        expectBuildException("invalid-queryset",
                "Cannot specify 'id' and 'refid' attributes together in queryset.");
    }

    @Test
    public void testWithReferenceQuerySet()
    {
        final String targetName = "test-queryset-reference";

        final Export export = (Export) getFirstStepFromTarget(targetName);

        final List tables = export.getTables();

        assertThat(tables).as("total count").hasSize(1);

        final QuerySet queryset = (QuerySet) tables.get(0);
        final Query testTable = (Query) queryset.getQueries().get(0);
        final Query secondTable = (Query) queryset.getQueries().get(1);

        assertThat(testTable.getName()).as("name").isEqualTo("TEST_TABLE");
        assertThat(testTable.getSql())
        .as("sql").isEqualTo("SELECT * FROM TEST_TABLE WHERE COLUMN0 = 'row0 col0'");

        assertThat(secondTable.getName()).as("name").isEqualTo("SECOND_TABLE");
        assertThat(secondTable.getSql())
        .as("sql").isEqualTo("SELECT B.* FROM TEST_TABLE A, SECOND_TABLE B "
                + "WHERE A.COLUMN0 = 'row0 col0' AND B.COLUMN0 = A.COLUMN0");

    }

    @Test
    public void testExportQueryMixed()
    {
        final String targetName = "test-export-query-mixed";
        final Export export = (Export) getFirstStepFromTarget(targetName);
        assertThat(export.getFormat()).as("format").isEqualTo("flat");

        final List tables = export.getTables();
        assertThat(tables).as("total count").hasSize(2);
        assertThat(getTableCount(tables)).as("table count").isEqualTo(1);
        assertThat(getQueryCount(tables)).as("query count").isEqualTo(1);

        final Table testTable = (Table) tables.get(0);
        assertThat(testTable.getName()).as("name").isEqualTo("TEST_TABLE");

        final Query pkTable = (Query) tables.get(1);
        assertThat(pkTable.getName()).as("name").isEqualTo("PK_TABLE");
    }

    /**
     * Tests the exception that is thrown when the compare fails because the
     * source format was different from the previous "export" task's write
     * format.
     */
    @Test
    public void testExportAndCompareFormatMismatch()
    {
        final String targetName = "test-export-and-compare-format-mismatch";

        try
        {
            getFirstTargetTask(targetName);
            fail("Should not be able to invoke ant task where the expected table was not found because it was tried to read in the wrong format.");
        } catch (final BuildException expected)
        {
            final Throwable cause = expected.getCause();
            assertThat(cause).isInstanceOf(DatabaseUnitException.class);
            final DatabaseUnitException dbUnitException =
                    (DatabaseUnitException) cause;
            final String filename =
                    new File(outputDir, "antExportDataSet.xml").toString();
            final String expectedMsg = "Did not find table in source file '"
                    + filename + "' using format 'xml'";
            assertThat(dbUnitException.getMessage()).isEqualTo(expectedMsg);
            assertThat(dbUnitException.getCause())
            .isInstanceOf(NoSuchTableException.class);
            final NoSuchTableException nstException =
                    (NoSuchTableException) dbUnitException.getCause();
            assertThat(nstException.getMessage()).isEqualTo("TEST_TABLE");
        }
    }

    @Test
    public void testDataTypeFactory() throws Exception
    {
        final String targetName = "test-datatypefactory";
        final DbUnitTask task = getFirstTargetTask(targetName);

        final IDatabaseConnection connection = task.createConnection();
        final IDataTypeFactory factory =
                (IDataTypeFactory) connection.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY);

        final Class expectedClass = OracleDataTypeFactory.class;
        assertThat(factory.getClass()).as("factory").isEqualTo(expectedClass);
    }

    @Test
    public void testEscapePattern() throws Exception
    {
        final String targetName = "test-escapepattern";
        final DbUnitTask task = getFirstTargetTask(targetName);

        final IDatabaseConnection connection = task.createConnection();
        final String actualPattern = (String) connection.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN);

        final String expectedPattern = "[?]";
        assertThat(expectedPattern).as("factory").isEqualTo(actualPattern);
    }

    @Test
    public void testDataTypeFactoryViaGenericConfig() throws Exception
    {
        final String targetName = "test-datatypefactory-via-generic-config";
        final DbUnitTask task = getFirstTargetTask(targetName);

        final IDatabaseConnection connection = task.createConnection();

        final DatabaseConfig config = connection.getConfig();

        final IDataTypeFactory factory = (IDataTypeFactory) config
                .getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY);
        final Class expectedClass = OracleDataTypeFactory.class;
        assertThat(factory.getClass()).as("factory").isEqualTo(expectedClass);

        final String[] actualTableType = (String[]) config
                .getProperty(DatabaseConfig.PROPERTY_TABLE_TYPE);
        assertThat(actualTableType).as("tableType")
        .isEqualTo(new String[] {"TABLE", "SYNONYM"});
        assertThat(connection.getConfig()
                .getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
        .as("batched statements feature should be true")
        .isTrue();
        assertThat(connection.getConfig()
                .getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES))
        .as("qualified tablenames feature should be true")
        .isTrue();
    }

    @Test
    public void testClasspath() throws Exception
    {
        final String targetName = "test-classpath";

        try
        {
            rule.executeTarget(targetName);
            fail("Should not be able to connect with invalid url!");
        } catch (final BuildException e)
        {
            // Verify exception type
            assertThat(e.getCause()).isInstanceOf(SQLException.class);
        }

    }

    @Test
    public void testDriverNotInClasspath() throws Exception
    {
        final String targetName = "test-drivernotinclasspath";

        try
        {
            rule.executeTarget(targetName);
            fail("Should not have found driver!");
        } catch (final BuildException e)
        {
            // Verify exception type
            assertThat(e.getCause()).as("nested exception type")
            .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    public void testReplaceOperation() throws Exception
    {
        final String targetName = "test-replace";
        final IDatabaseTester dbTest =
                DatabaseEnvironment.getInstance().getDatabaseTester();
        rule.executeTarget(targetName);
        final IDataSet ds = dbTest.getConnection().createDataSet();
        final ITable table = ds.getTable("PK_TABLE");
        assertThat(table.getValue(0, "NORMAL0")).isNull();
        assertThat(table.getValue(1, "NORMAL0")).isEqualTo("row 1");
    }

    @Test
    public void testOrderedOperation() throws Exception
    {
        final String targetName = "test-ordered";
        final IDatabaseTester dbTest =
                DatabaseEnvironment.getInstance().getDatabaseTester();
        rule.executeTarget(targetName);
        final IDataSet ds = dbTest.getConnection().createDataSet();
        final ITable table = ds.getTable("PK_TABLE");
        assertEquals("row 0", table.getValue(0, "NORMAL0"));
        assertEquals("row 1", table.getValue(1, "NORMAL0"));
    }

    @Test
    public void testReplaceOrderedOperation() throws Exception
    {
        final String targetName = "test-replace-ordered";
        final IDatabaseTester dbTest =
                DatabaseEnvironment.getInstance().getDatabaseTester();
        rule.executeTarget(targetName);
        final IDataSet ds = dbTest.getConnection().createDataSet();
        final ITable table = ds.getTable("PK_TABLE");
        assertNull(table.getValue(0, "NORMAL0"));
        assertEquals("row 1", table.getValue(1, "NORMAL0"));
    }

    protected void assertOperationType(final String failMessage,
            final String targetName, final DatabaseOperation expected)
    {
        final Operation oper = (Operation) getFirstStepFromTarget(targetName);
        final DatabaseOperation dbOper = oper.getDbOperation();
        assertThat(dbOper).as(failMessage + ", but was: " + dbOper)
        .isEqualTo(expected);
    }

    protected int getQueryCount(final List tables)
    {
        int count = 0;
        for (final Iterator it = tables.iterator(); it.hasNext();)
        {
            if (it.next() instanceof Query)
            {
                count++;
            }
        }

        return count;
    }

    protected int getTableCount(final List tables)
    {
        int count = 0;
        for (final Iterator it = tables.iterator(); it.hasNext();)
        {
            if (it.next() instanceof Table)
            {
                count++;
            }
        }

        return count;
    }

    protected int getQuerySetCount(final List tables)
    {
        int count = 0;
        for (final Iterator it = tables.iterator(); it.hasNext();)
        {
            if (it.next() instanceof QuerySet)
            {
                count++;
            }
        }

        return count;
    }

    protected DbUnitTaskStep getFirstStepFromTarget(final String targetName)
    {
        return getStepFromTarget(targetName, 0);
    }

    protected DbUnitTaskStep getStepFromTarget(final String targetName,
            final int index)
    {
        final DbUnitTask task = getFirstTargetTask(targetName);
        final List steps = task.getSteps();
        if (steps == null || steps.size() == 0)
        {
            fail("Can't get a dbunit <step> from the target: " + targetName
                    + ". No steps available.");
        }

        return (DbUnitTaskStep) steps.get(index);
    }

    private DbUnitTask getFirstTargetTask(final String targetName)
    {
        final Hashtable targets = rule.getProject().getTargets();
        rule.executeTarget(targetName);
        final Target target = (Target) targets.get(targetName);

        DbUnitTask task = null;

        final Object[] tasks = target.getTasks();
        // See https://ant.apache.org/faq.html#unknownelement.taskcontainer for
        // this change
        for (int i = 0; i < tasks.length; i++)
        {
            if (tasks[i] instanceof UnknownElement)
            {
                ((UnknownElement) tasks[i]).maybeConfigure();
                final Task elm = ((UnknownElement) tasks[i]).getTask();
                if (elm instanceof DbUnitTask)
                {
                    task = (DbUnitTask) elm;
                    task.getSteps().forEach(s -> {
                        try
                        {
                            ((DbUnitTaskStep)s).execute(((DbUnitTask) elm).createConnection());
                        } catch (DatabaseUnitException | SQLException e)
                        {
                            log.error("getFirstTargetTask: Error creating connection", e);
                        }
                    });
                }
            }
        }

        return task;
    }

    /**
     * Runs a target, wait for a build exception.
     *
     * @param target
     *            target to run
     * @param cause
     *            information string to reader of report
     * @param msg
     *            the message value of the build exception we are waiting for
     *            set to null for any build exception to be valid
     */
    public void expectSpecificBuildException(final String target,
            final String cause, final String msg)
    {
        try
        {
            rule.executeTarget(target);

        } catch (final BuildException ex)
        {

            assertThat(ex.getMessage())
            .as("Should throw BuildException because '" + cause
                    + "' with message '" + msg + "' (actual message '"
                    + ex.getMessage() + "' instead)")
            .satisfiesAnyOf(check -> assertThat(msg).isNull(),
                    check -> assertThat(check).isNotNull()
                    .isEqualTo(msg));

            return;
        }
        fail("Should throw BuildException because: " + cause);
    }

    public void expectBuildException(final String target, final String cause)
    {
        expectSpecificBuildException(target, cause, null);
    }
}
