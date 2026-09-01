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
import org.dbunit.util.fileloader.DataSetPathsProvider;

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
 * @see org.dbunit.VerifyTableDefinitionsProvider
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
     * <p>Mutually exclusive with {@link #provider()}; setting both is rejected.
     *
     * @return Zero or more dataset resource paths.
     */
    String[] value() default {};

    /**
     * A {@link DataSetPathsProvider} implementation to reflectively instantiate and ask for
     * the expected dataset paths, for a path list shared across several test classes.
     *
     * <p>Mutually exclusive with {@link #value()}; setting both is rejected. The provider
     * returning {@code null} or an empty array is rejected too - it is wired to supply paths,
     * so supplying none is a misconfiguration.
     *
     * @return The provider class; the interface itself (the default) means "not set".
     */
    Class<? extends DataSetPathsProvider> provider() default DataSetPathsProvider.class;

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
     * constants from - either a {@link org.dbunit.VerifyTableDefinitionsProvider}
     * implementation, or a plain class exposing {@code public static final
     * VerifyTableDefinition} fields. Overrides
     * {@link DbUnitConfig#verifyDefinitions()} when both are set.
     *
     * @return The catalog classes; empty (the default) means "not set".
     */
    Class<?>[] verifyDefinitions() default {};
}
