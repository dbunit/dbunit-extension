/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2025, DbUnit.org
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
package org.dbunit.junit.jupiter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.RowCountCheck;
import org.dbunit.database.rowcount.RowCountCheckConfiguration;
import org.dbunit.database.rowcount.RowCountChecker;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5/6 extension for DbUnit that manages the {@link IDatabaseTester} lifecycle
 * around each test method.
 *
 * <p>Calls {@link IDatabaseTester#onSetup()} immediately before the test method
 * (after all {@code @BeforeEach} callbacks) and {@link IDatabaseTester#onTearDown()}
 * immediately after the test method (before any {@code @AfterEach} callbacks).
 *
 * <p>The extension discovers the {@link IDatabaseTester} by scanning the test
 * instance's fields, including inherited fields, for a non-static field assignable
 * to {@link IDatabaseTester}: the nearest declaring class (test class before
 * superclass) wins, but that class must declare exactly one such field—two or
 * more at the same level is rejected as ambiguous. Configure the tester—including
 * its dataset—in a {@code @BeforeEach} method; those run before this extension's
 * setup callback:
 *
 * <pre>{@code
 * @ExtendWith(DbUnitExtension.class)
 * class MyDatabaseTest {
 *     IDatabaseTester databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *
 *     @BeforeEach
 *     void loadDataset() throws Exception {
 *         databaseTester.setDataSet(new FlatXmlDataSetBuilder().build(...));
 *     }
 *
 *     @Test
 *     void testSomething() { ... }
 * }
 * }</pre>
 *
 * <p><strong>Note:</strong> {@code @Nested} test classes are not supported. Field
 * discovery only scans the innermost test instance and its superclasses—not
 * enclosing class instances—since a Java nested class does not extend its
 * enclosing class.
 *
 * <p>Also runs the {@link RowCountCheck row count check} around the test, via the same
 * {@link RowCountChecker} that manages it for {@code DefaultPrepAndExpectedTestCase}: a
 * baseline is captured before {@code onSetup()}, and verified after {@code onTearDown()}
 * unless the test method itself threw, in which case the database is in an unknown state and
 * a count difference would be noise around the real failure. The check is opt-in and off by
 * default - see {@link RowCountCheckConfiguration}. A tester whose
 * {@link IDatabaseTester#getConnection()} returns {@code null} (e.g. a test double) is
 * tolerated; the check simply never activates for it. The connection {@link
 * IDatabaseTester#getConnection()} returns is never closed here - only its owner (the
 * tester's {@link org.dbunit.IOperationListener}, or the caller that constructed the
 * tester) knows whether it is safe to close, e.g. a {@code DefaultDatabaseTester} built
 * from one fixed connection returns that same connection from every call, and closing it
 * early would break the {@code onSetup()} that runs right after capturing the baseline.
 *
 * @author dbunit
 * @since 3.5.0
 */
public class DbUnitExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final Logger log = LoggerFactory.getLogger(DbUnitExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DbUnitExtension.class);

    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String TESTER_KEY = "databaseTester";
    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String ROW_COUNT_CHECKER_KEY = "rowCountChecker";

    /**
     * Runs database setup before the test method executes.
     *
     * @param context The extension context for the test method.
     * @throws Exception If resolving the {@link IDatabaseTester} field, capturing the row
     *             count check baseline, or its onSetup() call fails.
     */
    @Override
    public void beforeTestExecution(final ExtensionContext context) throws Exception {
        final IDatabaseTester tester = resolveTester(context);
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(TESTER_KEY, tester);
        store.put(ROW_COUNT_CHECKER_KEY, captureRowCountBaseline(tester));

        tester.onSetup();
    }

    /**
     * Runs database teardown after the test method executes, then verifies the row count
     * check baseline unless the test method itself threw.
     *
     * @param context The extension context for the test method.
     * @throws Exception If the stored {@link IDatabaseTester}'s onTearDown() call fails, or if
     *             a table's row count no longer matches the baseline
     *             ({@link org.dbunit.database.rowcount.UnexpectedRowCountException}).
     */
    @Override
    public void afterTestExecution(final ExtensionContext context) throws Exception {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        final IDatabaseTester tester = store.get(TESTER_KEY, IDatabaseTester.class);
        if (tester != null) {
            tester.onTearDown();

            final RowCountChecker rowCountChecker =
                    store.get(ROW_COUNT_CHECKER_KEY, RowCountChecker.class);
            if (rowCountChecker != null && rowCountChecker.hasBaseline()
                    && !context.getExecutionException().isPresent()) {
                verifyRowCountUnchanged(tester, rowCountChecker);
            }
        }
    }

    /**
     * Captures the row count check baseline for {@code tester} into a fresh
     * {@link RowCountChecker}, using whatever connection {@code tester.getConnection()}
     * returns - the same one {@code onSetup()} itself will use if the tester always returns
     * one fixed connection - without closing it: that connection's lifecycle belongs to the
     * tester, not to this check.
     *
     * @param tester the tester to capture a baseline for.
     * @return the checker holding the captured baseline, or {@code null} when {@code tester}
     *         has no connection to inspect. The returned checker holds no baseline - see
     *         {@link RowCountChecker#hasBaseline()} - when the check is disabled.
     * @throws Exception if resolving the connection or capturing the baseline fails.
     */
    private RowCountChecker captureRowCountBaseline(final IDatabaseTester tester)
            throws Exception {
        final IDatabaseConnection connection = tester.getConnection();
        if (connection == null) {
            return null;
        }
        final RowCountChecker rowCountChecker = new RowCountChecker();
        rowCountChecker.capture(connection);
        return rowCountChecker;
    }

    /**
     * Verifies {@code rowCountChecker}'s baseline against whatever connection
     * {@code tester.getConnection()} returns, without closing it - see
     * {@link #captureRowCountBaseline(IDatabaseTester)}.
     *
     * @param tester the tester to verify against.
     * @param rowCountChecker the checker holding the baseline captured by
     *            {@link #captureRowCountBaseline(IDatabaseTester)}.
     * @throws Exception if resolving the connection fails, or if a table's row count no longer
     *             matches the baseline.
     */
    private void verifyRowCountUnchanged(final IDatabaseTester tester,
            final RowCountChecker rowCountChecker) throws Exception {
        final IDatabaseConnection connection = tester.getConnection();
        if (connection == null) {
            return;
        }
        rowCountChecker.verify(connection);
    }

    private IDatabaseTester resolveTester(final ExtensionContext context) throws Exception {
        final Object testInstance = context.getTestInstance()
                .orElseThrow(() -> new IllegalStateException(
                        "No test instance available in ExtensionContext."));

        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            final Field field = findTesterField(clazz, testInstance);
            if (field != null) {
                field.setAccessible(true);
                final IDatabaseTester tester = (IDatabaseTester) field.get(testInstance);
                if (tester == null) {
                    throw new IllegalStateException("IDatabaseTester field '"
                            + field.getName() + "' in "
                            + testInstance.getClass().getName() + " is null.");
                }
                log.debug("Resolved IDatabaseTester '{}' in {}",
                        field.getName(), testInstance.getClass().getName());
                return tester;
            }
            clazz = clazz.getSuperclass();
        }

        throw new IllegalStateException("No IDatabaseTester field found in "
                + testInstance.getClass().getName()
                + " or its superclasses. Declare a non-static field whose type implements IDatabaseTester"
                + " to use DbUnitExtension.");
    }

    private Field findTesterField(final Class<?> clazz, final Object testInstance) {
        Field match = null;
        for (final Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && IDatabaseTester.class.isAssignableFrom(field.getType())) {
                if (match != null) {
                    throw new IllegalStateException("Multiple IDatabaseTester fields found in "
                            + clazz.getName() + ": '" + match.getName() + "' and '"
                            + field.getName() + "'. Declare exactly one non-static field"
                            + " whose type implements IDatabaseTester in "
                            + testInstance.getClass().getName() + ".");
                }
                match = field;
            }
        }
        return match;
    }
}
