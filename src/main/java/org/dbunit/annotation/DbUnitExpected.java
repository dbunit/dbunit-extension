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

import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.VerifyTableDefinition;

/**
 * Specifies the expected dataset file(s) to verify actual database state against after each
 * test method, and (optionally) which tables and rules to verify with. Presence of this
 * annotation is what switches the test from the setup/teardown path onto the
 * {@link PrepAndExpectedTestCase} prep/expected path - which runs dbUnit's expected-dataset
 * comparison for you. Leave it off, and the test method does its own asserting.
 *
 * <p>A method-level annotation overrides a class-level annotation on a per-test basis; the
 * class annotation is inherited by subclasses. Valid with no {@link DbUnitPrep} at all - the
 * database state already present is what gets verified.
 *
 * <p>An expected dataset must always be named, with {@link #value()}: the tables verified are
 * compared to it, so the forms below only choose which of its tables are compared. An
 * annotation naming none is rejected, whatever else it declares. A path shared through a
 * composed annotation should be absolute, as a relative one resolves against the class
 * carrying the composed annotation, not the class using it.
 *
 * <p>Which tables to verify, and with what rules, resolves through the following forms so the
 * common cases stay short - highest-priority form first:
 * <ol>
 *   <li>{@link #verify()} together with {@link #verifyDefinitions()} or {@link #verifyTables()}
 *   - rejected as ambiguous.</li>
 *   <li>{@link #verifyDefinitions()}, else - when this method declares neither it nor
 *   {@link #verify()} - {@link DbUnitConfig#verifyDefinitions()} as the class-level default -
 *   every definition in whichever named catalog class(es) is in play, narrowed to
 *   {@link #verifyTables()} when that is also given.</li>
 *   <li>{@link #verify()} - full per-table rules, inline.</li>
 *   <li>{@link #verifyTables()}, with no catalog in play (neither this method's own
 *   {@link #verifyDefinitions()} nor a class-level {@link DbUnitConfig#verifyDefinitions()}) -
 *   a default {@link VerifyTableDefinition} per named table.</li>
 *   <li>None of the above - a default {@link VerifyTableDefinition} per table in the expected
 *   dataset.</li>
 * </ol>
 *
 * <p>Example:
 * <pre>{@code
 * @DbUnitTest
 * class AccountRepositoryTest {
 *     IDatabaseTester databaseTester;
 *
 *     AccountRepositoryTest() throws ClassNotFoundException {
 *         databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *     }
 *
 *     @Test
 *     @DbUnitPrep("/dbunit/accounts/prep.xml")
 *     @DbUnitExpected(value = "/dbunit/accounts/expected.xml",
 *             verifyTables = {"ACCOUNT", "TRANSACTION"})
 *     void testWithdraw_sufficientBalance_decrementsBalance() { ... }
 * }
 * }</pre>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitPrep
 * @see DbUnitVerifyTable
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DbUnitExpected
{
    /**
     * Classpath resource paths for the expected dataset files to load.
     *
     * @return Zero or more dataset resource paths.
     */
    String[] value() default {};

    /**
     * Names of the tables to verify, using default verification rules - or, when
     * {@link #verifyDefinitions()} is also set, the subset of that catalog's tables to select.
     *
     * @return The table names; empty (the default) means "every table", the exact meaning
     *         depending on which other members are set - see the class Javadoc.
     */
    String[] verifyTables() default {};

    /**
     * Full per-table verification rules, inline.
     *
     * @return The table definitions; empty (the default) defers to {@link #verifyTables()} or
     *         {@link #verifyDefinitions()}.
     */
    DbUnitVerifyTable[] verify() default {};

    /**
     * One or more catalog classes to read shared {@link VerifyTableDefinition}
     * constants from: plain classes exposing {@code public static final
     * VerifyTableDefinition} fields. Overrides
     * {@link DbUnitConfig#verifyDefinitions()} when both are set.
     *
     * <p>A catalog is read once and shared by every test naming it for the whole run, its
     * {@link VerifyTableDefinition} objects included. A test must never change them, through
     * their setters such as {@code setSortOnFilteredColumnsOnly()} or
     * {@code setVerifyTableDefinitionVerifier()} included, and a
     * {@link org.dbunit.assertion.comparer.value.ValueComparer} or verifier inside one must be
     * stateless; otherwise the result of a later test depends on the order the tests ran in.
     *
     * @return The catalog classes; empty (the default) means "not set".
     */
    Class<?>[] verifyDefinitions() default {};
}
