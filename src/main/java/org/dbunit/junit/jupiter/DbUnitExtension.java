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
 * @author dbunit
 * @since 3.5.0
 */
public class DbUnitExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final Logger log = LoggerFactory.getLogger(DbUnitExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DbUnitExtension.class);

    private static final String TESTER_KEY = "databaseTester";

    /**
     * Runs database setup before the test method executes.
     *
     * @param context The extension context for the test method.
     * @throws Exception If resolving the {@link IDatabaseTester} field or its onSetup() call fails.
     */
    @Override
    public void beforeTestExecution(final ExtensionContext context) throws Exception {
        final IDatabaseTester tester = resolveTester(context);
        context.getStore(NAMESPACE).put(TESTER_KEY, tester);
        tester.onSetup();
    }

    /**
     * Runs database teardown after the test method executes.
     *
     * @param context The extension context for the test method.
     * @throws Exception If the stored {@link IDatabaseTester}'s onTearDown() call fails.
     */
    @Override
    public void afterTestExecution(final ExtensionContext context) throws Exception {
        final IDatabaseTester tester =
                context.getStore(NAMESPACE).get(TESTER_KEY, IDatabaseTester.class);
        if (tester != null) {
            tester.onTearDown();
        }
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
