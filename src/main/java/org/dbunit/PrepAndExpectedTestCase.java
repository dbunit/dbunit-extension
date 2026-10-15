/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import org.dbunit.dataset.IDataSet;

/**
 * Test case supporting prep data and expected data.
 *
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public interface PrepAndExpectedTestCase
{
    /**
     * Configure the test. Call this method before performing the test steps.
     *
     * @param verifyTableDefinitions
     *            Table definitions to verify after test execution; null means
     *            none.
     * @param prepDataFiles
     *            The prep data files (as classpath resources) to load and
     *            insert contents into the database as seed data; null means
     *            none.
     * @param expectedDataFiles
     *            The expected data files (as classpath resources) to load as
     *            expected data and verify actual data matches at test end; null
     *            means none.
     * @throws Exception if the test cannot be configured.
     */
    void configureTest(VerifyTableDefinition[] verifyTableDefinitions,
            String[] prepDataFiles, String[] expectedDataFiles)
            throws Exception;

    /**
     * Configure the test from a {@link PrepAndExpectedTestData} bundle instead of
     * the three separate array arguments. Equivalent to calling
     * {@link #configureTest(VerifyTableDefinition[], String[], String[])} with
     * the bundle's three values.
     *
     * @param testData
     *            The verification definitions and prep and expected data files
     *            for this test.
     * @throws Exception If the test cannot be configured.
     * @since 3.6.0
     */
    default void configureTest(final PrepAndExpectedTestData testData)
            throws Exception
    {
        configureTest(testData.getVerifyTableDefinitions(),
                testData.getPrepDataFiles(), testData.getExpectedDataFiles());
    }

    /**
     * Configure the test to verify every table of the expected data files, each with a default
     * {@link VerifyTableDefinition}, taking the tables from the expected dataset this
     * implementation loads for the comparison anyway. A caller that would otherwise parse the
     * expected files itself, only to list their tables for
     * {@link #configureTest(VerifyTableDefinition[], String[], String[])}, can let this
     * implementation parse them once.
     * <p>
     * Default method for binary compatibility with implementations predating this method, and
     * for ones that cannot derive the tables from the dataset they load: it configures nothing
     * and reports {@code false}, and the caller derives the definitions itself and calls
     * {@link #configureTest(VerifyTableDefinition[], String[], String[])}.
     *
     * @param prepDataFiles
     *            The prep data files (as classpath resources) to load and insert contents into
     *            the database as seed data; null means none.
     * @param expectedDataFiles
     *            The expected data files (as classpath resources) to load as expected data and
     *            verify actual data matches at test end; null means none.
     * @return {@code true} when this implementation configured the test, {@code false} when it
     *         did not and left the test unconfigured.
     * @throws Exception If the test cannot be configured.
     * @since 3.6.0
     */
    default boolean configureTestVerifyingEveryExpectedTable(final String[] prepDataFiles,
            final String[] expectedDataFiles) throws Exception
    {
        return false;
    }

    /**
     * Execute pre-test steps. Call this method before performing the test
     * steps.
     *
     * @throws Exception if the pre-test steps fail.
     */
    void preTest() throws Exception;

    /**
     * Convenience method to call configureTest() and preTest().
     *
     * @param verifyTables
     *            Table definitions to verify after test execution.
     * @param prepDataFiles
     *            The prep data files (as classpath resources) to load and
     *            insert contents into the database as seed data.
     * @param expectedDataFiles
     *            The expected data files (as classpath resources) to load as
     *            expected data and verify actual data matches at test end.
     * @throws Exception if the pre-test steps fail.
     */
    void preTest(VerifyTableDefinition[] verifyTables, String[] prepDataFiles,
            String[] expectedDataFiles) throws Exception;

    /**
     * Run the DbUnit test.
     *
     * @param verifyTables
     *            Table definitions to verify after test execution.
     * @param prepDataFiles
     *            The prep data files (as classpath resources) to load and
     *            insert contents into the database as seed data.
     * @param expectedDataFiles
     *            The expected data files (as classpath resources) to load as
     *            expected data and verify actual data matches at test end.
     * @param testSteps
     *            The test steps to run.
     * @return User defined object from running the test steps.
     * @throws Exception if the test steps fail.
     * @since 2.5.2
     */
    Object runTest(VerifyTableDefinition[] verifyTables, String[] prepDataFiles,
            String[] expectedDataFiles, PrepAndExpectedTestCaseSteps testSteps)
            throws Exception;

    /**
     * Execute all post-test steps. Call this method after performing the test
     * steps.
     *
     * @throws Exception if the post-test steps fail.
     */
    void postTest() throws Exception;

    /**
     * Execute post-test steps. Call this method after performing the test
     * steps.
     *
     * @param verifyData
     *            Specify true to perform verify data steps, false to not.
     *            Useful to specify false when test has failure in progress
     *            (e.g. an exception) and verifying data would fail, masking
     *            original test failure.
     * @throws Exception if the post-test steps fail.
     */
    void postTest(boolean verifyData) throws Exception;

    /**
     * For the provided VerifyTableDefinitions, verify each table's actual
     * results are as expected.
     *
     * @throws Exception if verifying the data fails.
     */
    void verifyData() throws Exception;

    /**
     * Cleanup tables specified in prep and expected datasets, using the
     * provided databaseTester. See
     * {@link org.dbunit.IDatabaseTester#onTearDown()}.
     *
     * @throws Exception if cleaning up the data fails.
     */
    void cleanupData() throws Exception;

    /**
     * Get the prep dataset, created from the prepDataFiles.
     *
     * @return The prep dataset.
     */
    IDataSet getPrepDataset();

    /**
     * Get the expected dataset, created from the expectedDataFiles.
     *
     * @return The expected dataset.
     */
    IDataSet getExpectedDataset();
}
