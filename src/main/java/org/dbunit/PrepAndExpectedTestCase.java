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

import java.util.Properties;

import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.DataFileLoader;

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
     *            Table definitions to verify after test execution.
     * @param prepDataFiles
     *            The prep data files (as classpath resources) to load and
     *            insert contents into the database as seed data.
     * @param expectedDataFiles
     *            The expected data files (as classpath resources) to load as
     *            expected data and verify actual data matches at test end.
     * @throws Exception if the test cannot be configured.
     */
    void configureTest(VerifyTableDefinition[] verifyTableDefinitions,
            String[] prepDataFiles, String[] expectedDataFiles)
            throws Exception;

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

    /**
     * Get the databaseTester this test case uses.
     * <p>
     * Default method for binary compatibility with implementations predating this method; they
     * report having no databaseTester of their own rather than failing to compile. An
     * implementation backed by {@link #setDatabaseTester(IDatabaseTester)} should override this
     * to return the value that method was last called with, the way
     * {@link DefaultPrepAndExpectedTestCase} does - doing so lets a caller resolving a tester on
     * this test case's behalf (e.g. {@code org.dbunit.junit.jupiter.DbUnitExtension}) keep the
     * two in sync instead of silently driving two different testers.
     *
     * @return The databaseTester, or {@code null} if none is set or this implementation does
     *         not support reporting one.
     * @since 3.6.0
     */
    default IDatabaseTester getDatabaseTester()
    {
        return null;
    }

    /**
     * Set the databaseTester this test case uses.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support being given a databaseTester after construction, so
     * a caller can invoke it unconditionally regardless of implementation. Override it, alongside
     * {@link #getDatabaseTester()}, to accept a tester resolved after construction - e.g. from a
     * {@code @DbUnitTester} field or a {@code DatabaseTesterFactory} - the way
     * {@link DefaultPrepAndExpectedTestCase} does.
     *
     * @param databaseTester The databaseTester to use.
     * @since 3.6.0
     */
    default void setDatabaseTester(final IDatabaseTester databaseTester)
    {
    }

    /**
     * Returns the connection this test case's own steps use for the current test, resolving one
     * from {@link #getDatabaseTester()} first if it has not already.
     * <p>
     * Default method for binary compatibility with implementations predating this method; its
     * default body simply asks {@link #getDatabaseTester()} for a connection independently on
     * every call - the same connection-identity limitation this method exists to let a caller
     * avoid. Override it, memoizing the result the way {@link DefaultPrepAndExpectedTestCase}
     * does, so repeated calls - and a caller resolving a connection on this test case's behalf -
     * reuse the same connection object instead of each opening a new one.
     * <p>
     * {@code org.dbunit.annotation}'s {@code AnnotatedTestExecutor} calls this - instead of
     * asking {@link #getDatabaseTester()} for a connection of its own - to resolve a
     * {@code Connection}/{@code IDatabaseConnection} parameter injection on the prep/expected
     * path, so the injected connection is the same one this test case's own setup/verify/cleanup
     * steps use rather than a second, independently-opened one. For a tester whose
     * {@code getConnection()} is not itself idempotent (e.g. a plain {@code JdbcDatabaseTester}),
     * a non-overriding implementation still returns a usable connection, just not necessarily
     * the same physical one this test case uses internally.
     *
     * @return The connection to reuse, or {@code null} if {@link #getDatabaseTester()} returns
     *         none.
     * @throws Exception On dbUnit errors.
     * @since 3.6.0
     */
    default IDatabaseConnection getReusableConnection() throws Exception
    {
        final IDatabaseTester databaseTester = getDatabaseTester();
        return databaseTester == null ? null : databaseTester.getConnection();
    }

    /**
     * Set the {@link DataFileLoader} this test case uses to load prepDataFiles/expectedDataFiles.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support being given a loader after construction. Override it
     * to receive a loader resolved after construction the way {@link DefaultPrepAndExpectedTestCase}
     * does.
     * <p>
     * {@code org.dbunit.annotation}'s {@code AnnotatedTestExecutor} calls this on a
     * {@code @DbUnitTestCase}-injected instance whenever {@code @DbUnitConfig.dataFileLoader()}
     * names a non-default loader, and throws {@code IllegalStateException} if this method is
     * not overridden - a silent no-op there would otherwise leave the instance loading with
     * whatever loader it was already constructed with.
     *
     * @param dataFileLoader The dataFileLoader to use.
     * @since 3.6.0
     */
    default void setDataFileLoader(final DataFileLoader dataFileLoader)
    {
    }

    /**
     * Set the {@link FailureHandler} this test case hands verifyData()'s assertion failures to.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support being given a failure handler after construction.
     * Override it to receive a handler resolved after construction the way
     * {@link DefaultPrepAndExpectedTestCase} does.
     * <p>
     * {@code org.dbunit.annotation}'s {@code AnnotatedTestExecutor} calls this on a
     * {@code @DbUnitTestCase}-injected instance whenever {@code @DbUnitConfig.failureHandler()}
     * is set, and throws {@code IllegalStateException} if this method is not overridden - a
     * silent no-op there would otherwise leave the configured handler applied nowhere.
     *
     * @param failureHandler The failureHandler to use.
     * @since 3.6.0
     */
    default void setFailureHandler(final FailureHandler failureHandler)
    {
    }

    /**
     * Set whether this test case closes its connection after each test.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support this being set after construction. Override it to
     * receive a value resolved after construction the way {@link DefaultPrepAndExpectedTestCase}
     * does.
     * <p>
     * {@code org.dbunit.annotation}'s {@code AnnotatedTestExecutor} calls this on a
     * {@code @DbUnitTestCase}-injected instance whenever
     * {@code @DbUnitConfig.closeConnectionAfterTest()} is {@code false}, and logs a warning if
     * this method is not overridden - unlike the other {@code @DbUnitConfig}-driven setters
     * this is not a complete no-op even then, since that executor's own connection (for the
     * row count check or parameter injection) still honors the value regardless; only this
     * test case's own connection handling might not.
     *
     * @param closeConnectionAfterTest True to close the connection after each test, false to
     *            leave it open.
     * @since 3.6.0
     */
    default void setCloseConnectionAfterTest(final boolean closeConnectionAfterTest)
    {
    }

    /**
     * Set DatabaseConfig property name/value pairs for this test case to apply to its connection.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support this being set after construction. Override it to
     * apply properties resolved after construction the way {@link DefaultPrepAndExpectedTestCase}
     * does.
     * <p>
     * {@code org.dbunit.annotation}'s {@code AnnotatedTestExecutor} calls this on a
     * {@code @DbUnitTestCase}-injected instance whenever
     * {@code @DbUnitConfig.properties()}/{@code propertiesProvider()} is non-empty, and throws
     * {@code IllegalStateException} if this method is not overridden - on the prep/expected
     * path this is the only route to the connection at all, so a silent no-op here would
     * otherwise apply the configured properties nowhere.
     *
     * @param databaseConfigProperties The properties to apply; null or empty applies none.
     * @since 3.6.0
     */
    default void setDatabaseConfigProperties(final Properties databaseConfigProperties)
    {
    }

    /**
     * Set the enabled flag and excluded table patterns this test case resolves its row count
     * check from, instead of its connection's DatabaseConfig.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support a row count check override. Override it, alongside
     * {@link #clearRowCountCheckOverride()}, to apply an override resolved after construction the
     * way {@link DefaultPrepAndExpectedTestCase} does.
     * <p>
     * {@code org.dbunit.annotation}'s {@code AnnotatedTestExecutor} calls this on a
     * {@code @DbUnitTestCase}-injected instance whenever {@code @DbUnitRowCountCheck} is
     * declared, and throws {@code IllegalStateException} if this method is not overridden - a
     * silent no-op here would otherwise leave the check silently never running for this test
     * at all.
     *
     * @param enabled Whether the check is enabled.
     * @param exclude The excluded table patterns; null is treated as empty (excludes none).
     * @since 3.6.0
     */
    default void setRowCountCheckOverride(final boolean enabled, final String[] exclude)
    {
    }

    /**
     * Clear a previously set row count check override, returning to resolving it from this test
     * case's connection's DatabaseConfig.
     * <p>
     * Default method for binary compatibility with implementations predating this method; it is
     * a no-op for one that does not support a row count check override. Override it, alongside
     * {@link #setRowCountCheckOverride(boolean, String[])}, the way
     * {@link DefaultPrepAndExpectedTestCase} does.
     * <p>
     * Unlike {@link #setRowCountCheckOverride(boolean, String[])}, {@code AnnotatedTestExecutor}
     * calls this unconditionally whenever {@code @DbUnitRowCountCheck} is absent, regardless of
     * whether this method is overridden - nothing was explicitly requested in that case, so
     * there is nothing to fail loud about; it is purely defensive, resetting a test case reused
     * across several tests (e.g. a {@code @DbUnitTestCase} static field) so an earlier test's
     * override does not silently carry over.
     *
     * @since 3.6.0
     */
    default void clearRowCountCheckOverride()
    {
    }
}
