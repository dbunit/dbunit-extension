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
 * <p>For an instance reused across methods this way that also caches its own
 * {@link PrepAndExpectedTestCase#getReusableConnection()} connection, the extension leaves that
 * connection alone when a test aborts before {@code configureTest()} (e.g. a
 * {@code @DbUnitConfig} value the instance's type cannot apply) - the instance keeps ownership
 * rather than the extension closing a connection the next method would reuse. A single-use
 * instance the extension constructs itself still has its connection closed on such a failure.
 *
 * <p>Marking this field does not, by itself, remove the need for a resolvable
 * {@code IDatabaseTester}: the extension's own machinery (installing the
 * {@code @DbUnitProperty} listener, and any {@code IDatabaseTester} parameter injection) needs
 * one regardless of test case type. For an implementation whose type overrides
 * {@link PrepAndExpectedTestCase#getDatabaseTester()}/
 * {@link PrepAndExpectedTestCase#setDatabaseTester(org.dbunit.IDatabaseTester)} - {@code
 * DefaultPrepAndExpectedTestCase} does - one is found and wired on automatically when none is
 * set yet. Any other implementation may override those same two methods to get the same
 * automatic wiring; one that does not (their default is a no-op reporting no tester) needs
 * {@link DbUnitConfig#databaseTesterFactory()} configured even when it manages its own
 * connection internally - otherwise resolution fails with "No IDatabaseTester field found" for
 * a test case that otherwise needs nothing external. A {@link DbUnitTester} field is not a
 * substitute here: it is rejected outright alongside this annotation (see above), and even if
 * it were not, {@code DbUnitExtension}'s tester resolution does not consult one for this
 * fallback. In that case the factory-resolved tester must be the exact instance this test case
 * actually uses internally, or {@code @DbUnitSetup}/{@code @DbUnitTearDown} operations set on
 * it may silently never reach what this test case actually runs.
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
