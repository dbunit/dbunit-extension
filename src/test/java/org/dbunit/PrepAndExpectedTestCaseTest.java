/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Properties;

import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.DataFileLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PrepAndExpectedTestCase}'s default method bodies directly, since every
 * implementation shipped in this codebase (e.g. {@link DefaultPrepAndExpectedTestCase})
 * overrides them - only a pre-3.6.0, third-party implementation predating these methods would
 * ever actually run the interface's own default bodies.
 *
 * @since 3.6.0
 */
class PrepAndExpectedTestCaseTest
{
    @Test
    void testGetDatabaseTester_notOverridden_returnsNull()
    {
        assertThat(new Bare().getDatabaseTester())
                .as("An implementation predating getDatabaseTester() - so it does not override"
                        + " it - must report no databaseTester via the default method rather"
                        + " than failing to compile.")
                .isNull();
    }

    @Test
    void testSetDatabaseTester_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().setDatabaseTester(new DefaultDatabaseTester(
                new MockDatabaseConnection())))
                .as("An implementation not overriding setDatabaseTester() must accept the call"
                        + " without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    @Test
    void testGetReusableConnection_getDatabaseTesterNotOverridden_returnsNull()
            throws Exception
    {
        assertThat(new Bare().getReusableConnection())
                .as("getDatabaseTester() returning null - the default, when it is not"
                        + " overridden either - must make getReusableConnection() report no"
                        + " connection too, matching its own documented contract, rather than"
                        + " throw a bare NullPointerException calling getConnection() on it.")
                .isNull();
    }

    @Test
    void testGetReusableConnection_getDatabaseTesterOverridden_delegatesToIt() throws Exception
    {
        final IDatabaseConnection connection = new MockDatabaseConnection();
        final IDatabaseTester tester = new DefaultDatabaseTester(connection);
        final PrepAndExpectedTestCase testCase = new Bare()
        {
            @Override
            public IDatabaseTester getDatabaseTester()
            {
                return tester;
            }
        };

        assertThat(testCase.getReusableConnection())
                .as("The default body must resolve the connection from getDatabaseTester(),"
                        + " when that is overridden to report one.")
                .isSameAs(connection);
    }

    @Test
    void testSetDataFileLoader_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().setDataFileLoader((DataFileLoader) null))
                .as("An implementation not overriding setDataFileLoader() must accept the call"
                        + " without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    @Test
    void testSetFailureHandler_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().setFailureHandler((FailureHandler) null))
                .as("An implementation not overriding setFailureHandler() must accept the call"
                        + " without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    @Test
    void testSetCloseConnectionAfterTest_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().setCloseConnectionAfterTest(true))
                .as("An implementation not overriding setCloseConnectionAfterTest() must accept"
                        + " the call without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    @Test
    void testSetDatabaseConfigProperties_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().setDatabaseConfigProperties(new Properties()))
                .as("An implementation not overriding setDatabaseConfigProperties() must accept"
                        + " the call without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    @Test
    void testSetRowCountCheckOverride_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().setRowCountCheckOverride(true, new String[0]))
                .as("An implementation not overriding setRowCountCheckOverride() must accept"
                        + " the call without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    @Test
    void testClearRowCountCheckOverride_notOverridden_isNoOp()
    {
        assertThatCode(() -> new Bare().clearRowCountCheckOverride())
                .as("An implementation not overriding clearRowCountCheckOverride() must accept"
                        + " the call without throwing, even though it does nothing with it.")
                .doesNotThrowAnyException();
    }

    /**
     * Implements only {@link PrepAndExpectedTestCase}'s abstract methods, so every default
     * method - the ones under test here - runs the interface's own body untouched.
     */
    private static class Bare implements PrepAndExpectedTestCase
    {
        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public void preTest()
        {
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final PrepAndExpectedTestCaseSteps testSteps)
        {
            return null;
        }

        @Override
        public void postTest()
        {
        }

        @Override
        public void postTest(final boolean verifyData)
        {
        }

        @Override
        public void verifyData()
        {
        }

        @Override
        public void cleanupData()
        {
        }

        @Override
        public IDataSet getPrepDataset()
        {
            return null;
        }

        @Override
        public IDataSet getExpectedDataset()
        {
            return null;
        }
    }
}
