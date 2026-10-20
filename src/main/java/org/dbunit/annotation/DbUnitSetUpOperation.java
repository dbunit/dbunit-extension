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
 * Specifies the database operation to apply before each test method, alongside
 * {@link DbUnitPrep} for the dataset to apply it to. It names an operation, not a dataset:
 * the files are {@link DbUnitPrep}'s.
 *
 * <p>A method-level annotation overrides a class-level annotation on a per-test basis; the
 * class annotation is inherited by subclasses. Declaring only {@link DbUnitPrep} is the
 * common case: the test then runs the setup operation the tester has, which is
 * {@link DbUnitOperation#CLEAN_INSERT} for dbUnit's built-in testers, so most tests never need
 * this annotation at all. Only this annotation replaces the tester's setup operation, for that
 * test only; a bare {@code @DbUnitSetUpOperation} replaces it with {@link #value()}'s default,
 * {@link DbUnitOperation#CLEAN_INSERT}.
 *
 * <p>Data ({@link DbUnitPrep}) and operation ({@link DbUnitSetUpOperation}) are deliberately
 * separate annotations rather than one fused annotation, so a class-level operation survives a
 * method-level {@link DbUnitPrep} instead of silently reverting to the default the moment a
 * method declares its own files.
 *
 * <p>This operation is the setup operation of the
 * {@link org.dbunit.DefaultPrepAndExpectedTestCase} that runs the test, for that test only; the
 * tester is not written and keeps its own. It acts on the {@link DbUnitPrep} dataset: a test that
 * declares none sets up no data, and the dataset a tester holds is never read. A
 * {@link DbUnitTestCase} field injecting an already-built test case is used as it was built, with
 * its tester's operations: declaring this annotation next to one fails fast with
 * {@link IllegalStateException}, so set the operation on the instance's tester where it is built.
 *
 * <p>Example — a class-wide {@link DbUnitOperation#REFRESH} operation, with each test method
 * naming its own prep file:
 * <pre>{@code
 * @DbUnitTest
 * @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
 * class UserRepositoryTest {
 *     IDatabaseTester databaseTester;
 *
 *     UserRepositoryTest() throws ClassNotFoundException {
 *         databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *     }
 *
 *     @Test
 *     @DbUnitPrep("/datasets/users.xml")
 *     void testFindAll() { ... }
 * }
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitPrep
 * @see DbUnitTearDownOperation
 * @see DbUnitOperation
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitSetUpOperation
{
    /**
     * The database operation to apply during setup.
     *
     * @return The setup operation; defaults to {@link DbUnitOperation#CLEAN_INSERT}.
     */
    DbUnitOperation value() default DbUnitOperation.CLEAN_INSERT;
}
