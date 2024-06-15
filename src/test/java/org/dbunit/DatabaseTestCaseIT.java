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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
class DatabaseTestCaseIT
{

    public void testTearDownExceptionDoesNotObscureTestException()
    {
        // TODO implement #1087040 tearDownOperation Exception obscures
        // underlying problem
    }

    /**
     * Tests whether the user can simply change the {@link DatabaseConfig} by
     * overriding the method
     * {@link DatabaseTestCase#setUpDatabaseConfig(DatabaseConfig)}.
     * 
     * @throws Exception
     */
    @Test
    void testConfigureConnection() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        final IDatabaseConnection conn = dbEnv.getConnection();

        final DatabaseTestCase testSubject = new DatabaseTestCase()
        {

            /**
             * method under test
             */
            @Override
            protected void setUpDatabaseConfig(final DatabaseConfig config)
            {
                config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE,
                        Integer.valueOf(97));
            }

            @Override
            protected IDatabaseConnection getConnection() throws Exception
            {
                return conn;
            }

            @Override
            protected IDataSet getDataSet() throws Exception
            {
                return null;
            }

            @Override
            protected DatabaseOperation getSetUpOperation() throws Exception
            {
                return DatabaseOperation.NONE;
            }

            @Override
            protected DatabaseOperation getTearDownOperation() throws Exception
            {
                return DatabaseOperation.NONE;
            }
        };

        // Simulate JUnit which first of all calls the "setUp" method
        testSubject.setUp();

        final IDatabaseConnection actualConn = testSubject.getConnection();
        assertThat(actualConn.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                        .isEqualTo(Integer.valueOf(97));

        final IDatabaseConnection actualConn2 =
                testSubject.getDatabaseTester().getConnection();
        assertThat(actualConn2.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                        .isEqualTo(Integer.valueOf(97));
    }
}
