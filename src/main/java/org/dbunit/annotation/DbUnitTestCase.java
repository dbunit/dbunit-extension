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
 * test case to drive: the extension runs that instance, as it was built, instead of
 * constructing one of its own, the "the object already exists and I want it used" case. Takes
 * precedence over a {@link DbUnitTester} field and a {@link DbUnitConfig#databaseTesterFactory()}.
 *
 * <p>Accepts a static field, the way a test case shared across every method in a class is
 * typically declared. Declaring this alongside a {@link DbUnitTester} field, or more than one
 * field with either marker at the same class level, is rejected as ambiguous.
 *
 * <p>The instance is used as its owner built it. The extension gives it the test's files and
 * calls {@code configureTest()}, {@code preTest()} and {@code postTest()}, and for a
 * {@link org.dbunit.DefaultPrepAndExpectedTestCase} applies {@link DbUnitRowCountCheck}
 * through its override methods, and nothing else. Its loader, failure handler, properties,
 * close-connection flag, tester and the tester's operations are the instance's own, set where it
 * is built, so a test declaring {@link DbUnitSetUpOperation}, {@link DbUnitTearDownOperation}
 * or a {@link DbUnitConfig} attribute that would configure it is rejected with
 * {@link IllegalStateException}. A {@link org.dbunit.DefaultPrepAndExpectedTestCase} must
 * already have a tester when it is resolved, and a data file loader when the test names
 * dataset files.
 *
 * <p>Any other implementation of {@link PrepAndExpectedTestCase} tells the extension of no
 * tester and no connection, so a test declaring anything that needs one -
 * {@code @DbUnitRowCountCheck}, a test without {@link DbUnitExpected}, or an
 * {@code IDatabaseTester} or {@code IDatabaseConnection} test method parameter - is rejected
 * with {@link IllegalStateException} too. Configure such a test case where it is built.
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
