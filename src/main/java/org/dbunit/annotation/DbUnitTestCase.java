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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.PrepAndExpectedTestCase;

/**
 * Marks a test class field, of a type implementing {@link PrepAndExpectedTestCase}, as the
 * test case to drive: the extension configures and runs that instance instead of constructing
 * one of its own, the "the object already exists and I want it used" case. Takes precedence
 * over a {@link DbUnitTester} field and a {@link DbUnitConfig#databaseTesterFactory()}.
 *
 * <p>Accepts a static field, the way a test case shared across every method in a class is
 * typically declared. Declaring this alongside a {@link DbUnitTester} field, or more than one
 * field with either marker at the same class level, is rejected as ambiguous.
 *
 * <p>The instance keeps the settings it was built with. {@link DbUnitConfig} pushes onto a
 * {@link org.dbunit.DefaultPrepAndExpectedTestCase} only what a test declares, for that test
 * only, and puts the instance's own values back afterward; see
 * {@link DbUnitConfig#dataFileLoader()} and {@link DbUnitConfig#closeConnectionAfterTest()} for
 * the two defaults that behave slightly differently. The extension leaves an instance's
 * connection alone when a test aborts before {@code configureTest()} (e.g. a
 * {@code @DbUnitConfig} value the instance cannot apply) - the instance keeps ownership
 * rather than the extension closing a connection the next method would reuse. A single-use
 * instance the extension constructs itself still has its connection closed on such a failure.
 *
 * <p>A {@link org.dbunit.DefaultPrepAndExpectedTestCase} with no tester is given the one the
 * extension resolves for the test, which it takes off again when the test is over. Any other
 * implementation of {@link PrepAndExpectedTestCase} is driven as it is: the extension gives it the
 * test's files and calls {@code configureTest()}, {@code preTest()} and {@code postTest()}, and
 * nothing else. It tells the extension of no tester and no connection, so a test declaring
 * anything that needs one - {@code @DbUnitSetUpOperation}, {@code @DbUnitTearDownOperation},
 * {@code @DbUnitRowCountCheck}, the {@link DbUnitConfig} attributes that configure the test
 * case, a test without {@link DbUnitExpected}, or an {@code IDatabaseTester},
 * {@code IDatabaseConnection} or {@code Connection} test method parameter - is rejected with
 * {@link IllegalStateException}. Configure such a test case where it is built.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitTester
 * @see DbUnitExpected
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbUnitTestCase
{
}
