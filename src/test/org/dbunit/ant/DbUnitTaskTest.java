/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.dbunit.ant;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.ext.mssql.InsertIdentityOperation;
import org.dbunit.ext.oracle.OracleDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.TaskdefsTest;

import java.io.File;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * Ant-based test class for the Dbunit ant task definition.
 *
 * @author Timothy Ruppert
 * @author Ben Cox
 * @version $Revision$
 * @since Jun 10, 2002
 * @see org.dbunit.ant.AntTest
 */
public class DbUnitTaskTest extends TaskdefsTest
{
    static protected Class classUnderTest = DbUnitTaskTest.class;

    public DbUnitTaskTest(String name)
    {
        super(name);
    }

    public void setUp() throws Exception
    {
        // This line ensure test database is initialized
        DatabaseEnvironment.getInstance();

        configureProject("src/xml/antTestBuildFile.xml");
    }

    public void testNoDriver()
    {
        expectBuildException("no-driver", "Should have required a driver attribute.");
    }

    public void testNoDbUrl()
    {
        expectBuildException("no-db-url", "Should have required a url attribute.");
    }

    public void testNoUserid()
    {
        expectBuildException("no-userid", "Should have required a userid attribute.");
    }

    public void testNoPassword()
    {
        expectBuildException("no-password", "Should have required a password attribute.");
    }

    public void testInvalidDatabaseInformation()
    {
        Throwable sql = null;
        try
        {
            executeTarget("invalid-db-info");
        }
        catch (BuildException e)
        {
            sql = e.getException();
        }
        finally
        {
            assertNotNull("Should have thrown a SQLException.", sql);
            assertTrue("Should have thrown a SQLException.", (sql instanceof SQLException));
        }
    }

    public void testInvalidOperationType()
    {
        Throwable iae = null;
        try
        {
            executeTarget("invalid-type");
        }
        catch (BuildException e)
        {
            iae = e.getException();
        }
        finally
        {
            assertNotNull("Should have thrown an IllegalArgumentException.", iae);
            assertTrue("Should have thrown an IllegalArgumentException.",
                    (iae instanceof IllegalArgumentException));
        }
    }

    public void testSetFlatFalse()
    {
        String targetName = "set-format-xml";
        Operation operation = (Operation)getFirstStepFromTarget(targetName);
        assertTrue("Operation attribute format should have been 'xml', but was: "
                + operation.getFormat(), operation.getFormat().equalsIgnoreCase("xml"));
    }

    public void testResolveOperationTypes()
    {
        assertOperationType("Should have been an DELETE_ALL operation",
                "set-type-none", DatabaseOperation.NONE);
        assertOperationType("Should have been an DELETE_ALL operation",
                "set-type-delete-all", DatabaseOperation.DELETE_ALL);
        assertOperationType("Should have been an INSERT operation",
                "set-type-insert", DatabaseOperation.INSERT);
        assertOperationType("Should have been an UPDATE operation",
                "set-type-update", DatabaseOperation.UPDATE);
        assertOperationType("Should have been an REFRESH operation",
                "set-type-refresh", DatabaseOperation.REFRESH);
        assertOperationType("Should have been an CLEAN_INSERT operation",
                "set-type-clean-insert", DatabaseOperation.CLEAN_INSERT);
        assertOperationType("Should have been an DELETE operation",
                "set-type-delete", DatabaseOperation.DELETE);
        assertOperationType("Should have been an MSSQL_INSERT operation",
                "set-type-mssql-insert", InsertIdentityOperation.INSERT);
        assertOperationType("Should have been an MSSQL_REFRESH operation",
                "set-type-mssql-refresh", InsertIdentityOperation.REFRESH);
        assertOperationType("Should have been an MSSQL_CLEAN_INSERT operation",
                "set-type-mssql-clean-insert", InsertIdentityOperation.CLEAN_INSERT);
    }

    public void testCompositeOrder()
    {
        String targetName = "composite-tests";
        Composite composite = (Composite)getFirstStepFromTarget(targetName);
        List operations = composite.getOperations();
        assertTrue("Composite should have had two suboperations, but has: "
                + operations.size(), operations.size() == 2);
        Operation cleanInsert = (Operation)operations.get(0);
        Operation delete = (Operation)operations.get(1);
        assertTrue("Should have been a CLEAN_INSERT, but was: " + cleanInsert.getType(),
                cleanInsert.getType().equals("CLEAN_INSERT"));
        assertTrue("Should have been a DELETE, but was: " + delete.getType(),
                delete.getType().equals("DELETE"));
    }

    public void testCompositeSrc()
    {
        String targetName = "composite-tests";
        Composite composite = (Composite)getFirstStepFromTarget(targetName);
        List operations = composite.getOperations();
        Iterator operIter = operations.listIterator();
        while (operIter.hasNext())
        {
            Operation operation = (Operation)operIter.next();
            File src = operation.getSrc();
            assertNotNull("Operation shouldn't have a null src!", src);
            assertTrue("Operation should have src from composite: " + composite.getSrc()
                    + ", but was: " + src,
                    src.equals(composite.getSrc()));
        }
    }

    public void testInvalidCompositeOperationSrc()
    {
        expectBuildException("invalid-composite-operation-src",
                "Should have objected to nested operation src attribute "
                + "being set.");
    }

    public void testInvalidCompositeOperationFlat()
    {
        expectBuildException("invalid-composite-operation-format-flat",
                "Should have objected to nested operation format attribute "
                + "being set.");
    }

    public void testExportFull()
    {
        String targetName = "test-export-full";
        Export export = (Export)getFirstStepFromTarget(targetName);
        assertTrue("Should have been a flat format, "
                + "but was: " + export.getFormat(),
                export.getFormat().equalsIgnoreCase("flat"));
        List tables = export.getTables();
        assertTrue("Should have been an empty table list "
                + "(indicating a full dataset), but was: "
                + tables, tables.size() == 0);
    }

    public void testExportPartial()
    {
        String targetName = "test-export-partial";
        Export export = (Export)getFirstStepFromTarget(targetName);
        List tables = export.getTables();
        assertTrue("Export should have had two subtables, but has: "
                + tables.size(), tables.size() == 2);
        Table testTable = (Table)tables.get(0);
        Table pkTable = (Table)tables.get(1);
        assertTrue("Should have been been TABLE TEST_TABLE, but was: "
                + testTable.getName(), testTable.getName().equals("TEST_TABLE"));
        assertTrue("Should have been been TABLE PK_TABLE, but was: "
                + pkTable.getName(), pkTable.getName().equals("PK_TABLE"));
    }

    public void testExportFlat()
    {
        String targetName = "test-export-format-flat";
        Export export = (Export)getFirstStepFromTarget(targetName);
        assertTrue("Should have been a flat format, "
                + "but was: " + export.getFormat(),
                export.getFormat().equalsIgnoreCase("flat"));
    }

    public void testExportXml()
    {
        String targetName = "test-export-format-xml";
        Export export = (Export)getFirstStepFromTarget(targetName);
        assertTrue("Should have been an xml format, "
                + "but was: " + export.getFormat(),
                export.getFormat().equalsIgnoreCase("xml"));
    }

    public void testExportDtd()
    {
        String targetName = "test-export-format-dtd";
        Export export = (Export)getFirstStepFromTarget(targetName);
        assertTrue("Should have been a dtd format, "
                + "but was: " + export.getFormat(),
                export.getFormat().equalsIgnoreCase("dtd"));
    }

    public void testInvalidExportFormat()
    {
        expectBuildException("invalid-export-format",
                "Should have objected to invalid format attribute.");
    }

    public void testExportQuery()
    {
        String targetName = "test-export-query";
        Export export = (Export)getFirstStepFromTarget(targetName);
        assertEquals("format", "flat", export.getFormat());

        List queries = export.getTables();
        assertEquals("query count", 2, getQueryCount(queries));

        Query testTable = (Query)queries.get(0);
        assertEquals("name", "TEST_TABLE", testTable.getName());
        assertEquals("sql", "SELECT * FROM test_table", testTable.getSql());

        Query pkTable = (Query)queries.get(1);
        assertEquals("name", "PK_TABLE", pkTable.getName());
        assertEquals("sql", "SELECT * FROM pk_table", pkTable.getSql());
    }

    public void testExportQueryMixed()
    {
        String targetName = "test-export-query-mixed";
        Export export = (Export)getFirstStepFromTarget(targetName);
        assertEquals("format", "flat", export.getFormat());

        List tables = export.getTables();
        assertEquals("total count", 2, tables.size());
        assertEquals("table count", 1, getTableCount(tables));
        assertEquals("query count", 1, getQueryCount(tables));

        Table testTable = (Table)tables.get(0);
        assertEquals("name", "TEST_TABLE", testTable.getName());

        Query pkTable = (Query)tables.get(1);
        assertEquals("name", "PK_TABLE", pkTable.getName());
    }

    public void testDataTypeFactory() throws Exception
    {
        String targetName = "test-datatypefactory";
        DbUnitTask task = getFirstTargetTask(targetName);

        IDatabaseConnection connection = task.createConnection();
        IDataTypeFactory factory = (IDataTypeFactory)connection.getConfig().getProperty(
                        DatabaseConfig.PROPERTY_DATATYPE_FACTORY);

        Class expectedClass = OracleDataTypeFactory.class;
        assertEquals("factory", expectedClass, factory.getClass());
    }

    public void testEscapePattern() throws Exception
    {
        String targetName = "test-escapepattern";
        DbUnitTask task = getFirstTargetTask(targetName);

        IDatabaseConnection connection = task.createConnection();
        String actualPattern = (String)connection.getConfig().getProperty(
                        DatabaseConfig.PROPERTY_ESCAPE_PATTERN);

        String expectedPattern = "[?]";
        assertEquals("factory", expectedPattern, actualPattern);
    }

    public void testClasspath() throws Exception
    {
        String targetName = "test-classpath";

        try
        {
            executeTarget(targetName);
            fail("Should not be able to connect with invalid url!");
        }
        catch (BuildException e)
        {
            // Verify exception type
            assertEquals("nested exception type", SQLException.class, e.getException().getClass());
        }

    }

    public void testDriverNotInClasspath() throws Exception
    {
        String targetName = "test-drivernotinclasspath";

        try
        {
            executeTarget(targetName);
            fail("Should not have found driver!");
        }
        catch (BuildException e)
        {
            // Verify exception type
            assertEquals("nested exception type", ClassNotFoundException.class, e.getException().getClass());
        }
    }


    protected void assertOperationType(String failMessage, String targetName, DatabaseOperation expected)
    {
        Operation oper = (Operation)getFirstStepFromTarget(targetName);
        DatabaseOperation dbOper = oper.getDbOperation();
        assertTrue(failMessage + ", but was: " + dbOper, expected.equals(dbOper));
    }

    protected int getQueryCount(List tables)
    {
        int count = 0;
        for (Iterator it = tables.iterator(); it.hasNext();)
        {
            if (it.next() instanceof Query)
            {
                count++;
            }
        }

        return count;
    }

    protected int getTableCount(List tables)
    {
        int count = 0;
        for (Iterator it = tables.iterator(); it.hasNext();)
        {
            if (it.next() instanceof Table)
            {
                count++;
            }
        }

        return count;
    }

    protected DbUnitTaskStep getFirstStepFromTarget(String targetName)
    {
        DbUnitTaskStep result = null;
        DbUnitTask task = getFirstTargetTask(targetName);
        List steps = task.getSteps();
        if (steps != null && steps.size() > 0)
        {
            result = (DbUnitTaskStep)steps.get(0);
        }
        else
        {
            fail("Can't get a dbunit <step> from the target: " + targetName);
        }
        return result;
    }

    private DbUnitTask getFirstTargetTask(String targetName)
    {
        Hashtable targets = project.getTargets();
        executeTarget(targetName);
        Target target = (Target)targets.get(targetName);
        DbUnitTask task = (DbUnitTask)target.getTasks()[0];
        return task;
    }

    public static Test suite()
    {
        TestSuite suite = new TestSuite(classUnderTest);
        return suite;
    }

    public static void main(String args[])
    {
        if (args.length > 0 && args[0].equals("-gui"))
        {
            String[] testCaseName = {classUnderTest.getName()};
            junit.swingui.TestRunner.main(testCaseName);
        }
        else
        {
            junit.textui.TestRunner.run(suite());
        }
    }
}
