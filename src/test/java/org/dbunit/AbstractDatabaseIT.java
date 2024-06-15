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
package org.dbunit;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.SortedTable;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
public abstract class AbstractDatabaseIT extends DatabaseTestCase
{
    protected IDatabaseConnection _connection;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected DatabaseEnvironment getEnvironment() throws Exception
    {
        return DatabaseEnvironment.getInstance();
    }

    protected ITable createOrderedTable(final String tableName,
            final String orderByColumn) throws Exception
    {
        return new SortedTable(_connection.createDataSet().getTable(tableName),
                new String[] {orderByColumn});
        // String sql = "select * from " + tableName + " order by " +
        // orderByColumn;
        // return _connection.createQueryTable(tableName, sql);
    }

    /**
     * Returns the string converted as an identifier according to the metadata
     * rules of the database environment. Most databases convert all metadata
     * identifiers to uppercase. PostgreSQL converts identifiers to lowercase.
     * MySQL preserves case.
     *
     * @param str
     *            The identifier.
     * @return The identifier converted according to database rules.
     */
    protected String convertString(final String str) throws Exception
    {
        return getEnvironment().convertString(str);
    }

    ////////////////////////////////////////////////////////////////////////////
    // TestCase class

    @Override
    @BeforeEach
    protected void setUp() throws Exception
    {
        super.setUp();

        _connection = getDatabaseTester().getConnection();
        setUpDatabaseConfig(_connection.getConfig());
    }

    @Override
    protected IDatabaseTester getDatabaseTester() throws Exception
    {
        try
        {
            return getEnvironment().getDatabaseTester();
        } catch (final Exception e)
        {
            // TODO matthias: this here hides original exceptions from being
            // shown in the
            // JUnit results
            // (logger is not configured for unit tests). Think about how
            // exceptions can be
            // passed through
            // So I temporarily added the "e.printStackTrace()"...
            logger.error("getDatabaseTester()", e);
            e.printStackTrace();
        }
        return super.getDatabaseTester();
    }

    @Override
    protected void setUpDatabaseConfig(final DatabaseConfig config)
    {
        try
        {
            getEnvironment().setupDatabaseConfig(config);
        } catch (final Exception ex)
        {
            throw new RuntimeException(ex); // JH_TODO: is this the "DbUnit way"
                                            // to handle exceptions?
        }
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception
    {
        super.tearDown();

        DatabaseOperation.DELETE_ALL.execute(_connection,
                _connection.createDataSet());

        _connection.close();

        _connection = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // DatabaseTestCase class

    @Override
    protected IDatabaseConnection getConnection() throws Exception
    {
        final IDatabaseConnection connection = getEnvironment().getConnection();
        return connection;

        // return new
        // DatabaseEnvironment(getEnvironment().getProfile()).getConnection();
        // return new DatabaseConnection(connection.getConnection(),
        // connection.getSchema());
    }

    @Override
    protected IDataSet getDataSet() throws Exception
    {
        return getEnvironment().getInitDataSet();
    }

    protected void closeConnection(final IDatabaseConnection connection)
            throws Exception
    {
        // getEnvironment().closeConnection();
    }
    //
    // protected DatabaseOperation getTearDownOperation() throws Exception
    // {
    // return DatabaseOperation.DELETE_ALL;
    // }

    /**
     * This method is used so sub-classes can disable the tests according to
     * some characteristics of the environment
     *
     * @param testName
     *            name of the test to be checked
     * @return flag indicating if the test should be executed or not
     */
    protected boolean runTest(final String testName)
    {
        return true;
    }

    protected void runTest() throws Throwable
    {
        if (runTest(getName()))
        {
            // super.runTest();
        } else
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Skipping test " + getClass().getName() + "."
                        + getName());
            }
        }
    }

    public static boolean environmentHasFeature(final TestFeature feature)
    {
        try
        {
            final DatabaseEnvironment environment =
                    DatabaseEnvironment.getInstance();
            final boolean runIt = environment.support(feature);
            return runIt;
        } catch (final Exception e)
        {
            throw new DatabaseUnitRuntimeException(e);
        }
    }

}
