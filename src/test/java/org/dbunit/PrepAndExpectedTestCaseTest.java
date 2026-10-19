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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PrepAndExpectedTestCase}'s default method bodies directly, since the
 * implementation shipped in this codebase ({@link DefaultPrepAndExpectedTestCase}) overrides
 * the {@link PrepAndExpectedTestData} ones to honor a bundle that verifies every expected table:
 * only another implementation ever runs the interface's own default bodies.
 *
 * @since 3.6.0
 */
class PrepAndExpectedTestCaseTest
{
    @Test
    void testConfigureTest_everyExpectedTableBundleAndNotOverridden_throwsUnsupportedOperationException()
    {
        final PrepAndExpectedTestData everyTable = PrepAndExpectedTestData
                .verifyingExpectedTables(new String[0], new String[] {"expected.xml"});

        assertThatThrownBy(() -> new Bare().configureTest(everyTable))
                .as("An implementation that does not override the bundle method cannot take the"
                        + " tables from the dataset it loads, and must not silently verify"
                        + " nothing.")
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(Bare.class.getName())
                .hasMessageContaining("verifyingExpectedTables");
    }

    @Test
    void testPreTest_everyExpectedTableBundleAndNotOverridden_throwsUnsupportedOperationException()
    {
        final PrepAndExpectedTestData everyTable = PrepAndExpectedTestData
                .verifyingExpectedTables(new String[0], new String[] {"expected.xml"});

        assertThatThrownBy(() -> new Bare().preTest(everyTable))
                .as("preTest() has the same refusal as configureTest().")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testRunTest_everyExpectedTableBundleAndNotOverridden_throwsUnsupportedOperationException()
    {
        final PrepAndExpectedTestData everyTable = PrepAndExpectedTestData
                .verifyingExpectedTables(new String[0], new String[] {"expected.xml"});

        assertThatThrownBy(() -> new Bare().runTest(everyTable, () -> null))
                .as("runTest() has the same refusal as configureTest().")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testConfigureTest_testDataOverload_forwardsBundleContentsToArrayOverload()
            throws Exception
    {
        final RecordingTestCase testCase = new RecordingTestCase();
        final VerifyTableDefinition table =
                new VerifyTableDefinition("ACCOUNT", new String[] {});
        final PrepAndExpectedTestData testData = new PrepAndExpectedTestData(
                new VerifyTableDefinition[] {table},
                new String[] {"/prep/accounts.xml"},
                new String[] {"/expected/accounts-after.xml"});

        testCase.configureTest(testData);

        assertThat(testCase.verifyTables)
                .as("configureTest(testData) must forward the bundle's verify"
                        + " definitions to the array overload.")
                .containsExactly(table);
        assertThat(testCase.prepDataFiles)
                .as("configureTest(testData) must forward the bundle's prep files"
                        + " to the array overload.")
                .containsExactly("/prep/accounts.xml");
        assertThat(testCase.expectedDataFiles)
                .as("configureTest(testData) must forward the bundle's expected"
                        + " files to the array overload.")
                .containsExactly("/expected/accounts-after.xml");
    }

    @Test
    void testPreTest_testDataOverload_forwardsBundleContentsToArrayOverload()
            throws Exception
    {
        final RecordingTestCase testCase = new RecordingTestCase();
        final PrepAndExpectedTestData testData = new PrepAndExpectedTestData(null,
                new String[] {"/prep/accounts.xml"},
                new String[] {"/expected/accounts-after.xml"});

        testCase.preTest(testData);

        assertThat(testCase.prepDataFiles)
                .as("preTest(testData) must forward the bundle's prep files to the"
                        + " array overload.")
                .containsExactly("/prep/accounts.xml");
        assertThat(testCase.expectedDataFiles)
                .as("preTest(testData) must forward the bundle's expected files to"
                        + " the array overload.")
                .containsExactly("/expected/accounts-after.xml");
    }

    @Test
    void testRunTest_testDataOverload_forwardsBundleAndStepsAndReturnsArrayOverloadResult()
            throws Exception
    {
        final RecordingTestCase testCase = new RecordingTestCase();
        final PrepAndExpectedTestCaseSteps steps = () -> null;
        final PrepAndExpectedTestData testData =
                PrepAndExpectedTestData.prepOnly("/prep/accounts.xml");

        final Object result = testCase.runTest(testData, steps);

        assertThat(testCase.prepDataFiles)
                .as("runTest(testData, steps) must forward the bundle's prep files"
                        + " to the array overload.")
                .containsExactly("/prep/accounts.xml");
        assertThat(testCase.ranSteps)
                .as("runTest(testData, steps) must forward the test steps"
                        + " unchanged to the array overload.")
                .isSameAs(steps);
        assertThat(result)
                .as("runTest(testData, steps) must return exactly what the array"
                        + " overload returned.")
                .isSameAs(testCase.runTestResult);
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

    /**
     * Records the arguments the {@link PrepAndExpectedTestData} default overloads pass on to
     * their array-based counterparts, so a test can assert the bundle was unpacked and
     * forwarded correctly.
     */
    private static final class RecordingTestCase extends Bare
    {
        private final Object runTestResult = new Object();

        private VerifyTableDefinition[] verifyTables;
        private String[] prepDataFiles;
        private String[] expectedDataFiles;
        private PrepAndExpectedTestCaseSteps ranSteps;

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
            record(verifyTables, prepDataFiles, expectedDataFiles);
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
            record(verifyTables, prepDataFiles, expectedDataFiles);
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final PrepAndExpectedTestCaseSteps testSteps)
        {
            record(verifyTables, prepDataFiles, expectedDataFiles);
            ranSteps = testSteps;
            return runTestResult;
        }

        private void record(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
            this.verifyTables = verifyTables;
            this.prepDataFiles = prepDataFiles;
            this.expectedDataFiles = expectedDataFiles;
        }
    }
}
