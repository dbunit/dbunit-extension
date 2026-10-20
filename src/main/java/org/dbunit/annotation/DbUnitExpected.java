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
 * annotation is what has the {@link PrepAndExpectedTestCase} that runs the test run dbUnit's
 * expected-dataset comparison for you. Leave it off, and the test method does its own
 * asserting.
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
 * common cases stay short. The expected dataset, or {@link #verifyTables()} when given, decides
 * which tables are verified; a class-level {@link DbUnitConfig#verifyDefinitions() catalog} only
 * supplies each table's rules:
 * <ol>
 *   <li>{@link #verify()} - full per-table rules, inline, for exactly the tables it names.
 *   Rejected together with {@link #verifyTables()}.</li>
 *   <li>{@link #verifyTables()} - exactly these tables, each with its definition from the
 *   catalog when the catalog defines it and a default {@link VerifyTableDefinition} when not.</li>
 *   <li>None of the above - every table in the expected dataset, with the catalog's definition
 *   for the tables it defines and a default {@link VerifyTableDefinition} for the others. A
 *   catalog table the expected dataset lacks is not verified.</li>
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
     * Names of the tables to verify, with the rules of the class-level
     * {@link DbUnitConfig#verifyDefinitions() catalog} for a table it defines and default rules
     * for the others.
     *
     * @return The table names; empty (the default) means every table of the expected dataset.
     */
    String[] verifyTables() default {};

    /**
     * Full per-table verification rules, inline.
     *
     * @return The table definitions; empty (the default) defers to {@link #verifyTables()} or,
     *         with that empty too, to every table of the expected dataset.
     */
    DbUnitVerifyTable[] verify() default {};
}
