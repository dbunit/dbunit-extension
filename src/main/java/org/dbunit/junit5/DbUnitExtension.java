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
package org.dbunit.junit5;

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
 * instance's fields, including inherited fields, for the first non-static field
 * assignable to {@link IDatabaseTester}. Configure the tester—including its
 * dataset—in a {@code @BeforeEach} method; those run before this extension's
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
 * @author dbunit
 * @since 3.2.0
 */
public class DbUnitExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final Logger log = LoggerFactory.getLogger(DbUnitExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DbUnitExtension.class);

    private static final String TESTER_KEY = "databaseTester";

    @Override
    public void beforeTestExecution(final ExtensionContext context) throws Exception {
        final IDatabaseTester tester = resolveTester(context);
        context.getStore(NAMESPACE).put(TESTER_KEY, tester);
        tester.onSetup();
    }

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
            for (final Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && IDatabaseTester.class.isAssignableFrom(field.getType())) {
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
            }
            clazz = clazz.getSuperclass();
        }

        throw new IllegalStateException("No IDatabaseTester field found in "
                + testInstance.getClass().getName()
                + " or its superclasses. Declare a non-static field of type IDatabaseTester"
                + " to use DbUnitExtension.");
    }
}
