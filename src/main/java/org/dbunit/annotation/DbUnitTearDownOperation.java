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
package org.dbunit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.operation.DbUnitOperation;

/**
 * Specifies the database operation to apply after each test method.
 *
 * <p>A method-level annotation overrides a class-level annotation; the class annotation is
 * inherited by subclasses. Unlike {@link DbUnitSetUpOperation}, teardown has no dataset
 * counterpart: the dataset already prepared - the union of the prep and expected data, when
 * {@link DbUnitExpected} is present - is what the operation runs against. Declaring an extra
 * table to tear down is done by listing it, empty, in the prep dataset instead.
 *
 * <p>This operation is the teardown operation of the
 * {@link org.dbunit.DefaultPrepAndExpectedTestCase} that runs the test, for that test only; the
 * tester is not written and keeps its own. A {@link DbUnitTestCase} field injecting an
 * already-built test case is used as it was built, with its tester's operations: declaring this
 * annotation next to one fails fast with {@link IllegalStateException}, so set the operation on
 * the instance's tester where it is built.
 *
 * <p>Example — delete all rows after each test:
 * <pre>{@code
 * @DbUnitTest
 * @DbUnitPrep("/datasets/orders.json")
 * @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
 * class OrderRepositoryTest {
 *     IDatabaseTester databaseTester;
 *
 *     OrderRepositoryTest() throws ClassNotFoundException {
 *         databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *     }
 *
 *     @Test
 *     void testPlaceOrder() { ... }
 * }
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitSetUpOperation
 * @see DbUnitOperation
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitTearDownOperation
{
    /**
     * The database operation to apply during teardown.
     *
     * @return The teardown operation; defaults to {@link DbUnitOperation#NONE}.
     */
    DbUnitOperation value() default DbUnitOperation.NONE;
}
