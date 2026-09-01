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
 * {@link DbUnitPrep} for the dataset to apply it to.
 *
 * <p>A method-level annotation overrides a class-level annotation on a per-test basis; the
 * class annotation is inherited by subclasses. Declaring only {@link DbUnitPrep} is the
 * common case: {@link #operation()} defaults to {@link DbUnitOperation#CLEAN_INSERT} already,
 * so most tests never need this annotation at all.
 *
 * <p>Data ({@link DbUnitPrep}) and operation ({@link DbUnitSetup}) are deliberately separate
 * annotations rather than one fused annotation, so a class-level operation survives a
 * method-level {@link DbUnitPrep} instead of silently reverting to the default the moment a
 * method declares its own files.
 *
 * <p>On the prep/expected path ({@link DbUnitExpected} declared), this operation is applied to
 * the resolved {@code IDatabaseTester}, which only reaches the actual setup call when the test
 * case reads its own setup operation from that same tester the way
 * {@link org.dbunit.DefaultPrepAndExpectedTestCase} does. A {@link DbUnitTestCase} field
 * injecting a different implementation that manages its own tester internally leaves this
 * annotation silently unapplied there.
 *
 * <p>Example — a class-wide {@link DbUnitOperation#REFRESH} operation, with each test method
 * naming its own prep file:
 * <pre>{@code
 * @DbUnitTest
 * @DbUnitSetup(operation = DbUnitOperation.REFRESH)
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
 * @see DbUnitTearDown
 * @see DbUnitOperation
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitSetup
{
    /**
     * The database operation to apply during setup.
     *
     * @return The setup operation; defaults to {@link DbUnitOperation#CLEAN_INSERT}.
     */
    DbUnitOperation operation() default DbUnitOperation.CLEAN_INSERT;
}
