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
package org.dbunit;

/**
 * Produces a configured {@link IDatabaseTester} for
 * {@code org.dbunit.annotation.DbUnitConfig#databaseTesterFactory()}.
 *
 * <p>An {@code org.dbunit.annotation} factory class: an annotation attribute names it by
 * {@link Class}, dbUnit instantiates it once for the whole run through its own no-arg
 * constructor, and asks it for its one value - here {@link #getDatabaseTester()} - for each test.
 * A factory because {@link IDatabaseTester} implementations have no uniform no-arg constructor -
 * {@link JdbcDatabaseTester} needs a driver and URL, {@link DataSourceDatabaseTester} a
 * {@code DataSource}, {@link JndiDatabaseTester} a lookup name.
 *
 * <p>The one instance serves every test that names the class, and tests may run in parallel, so
 * a factory must be thread-safe. What must exist once, such as a pooled {@code DataSource},
 * belongs in the constructor of the factory, or is built on its first use, and stays in a field
 * of the factory. As {@link #getDatabaseTester()} runs for each test, the factory decides
 * whether the tests share one tester, which it then keeps in a field too, or each gets a new
 * tester. A constructor that throws leaves no instance behind, so the next test that names the
 * class tries again. dbUnit never closes the factory or the testers it returns, so a factory
 * that opens a resource closes it itself.
 *
 * <p>This is a general dbUnit concept, not specific to annotation-driven configuration, so it
 * lives beside {@link IDatabaseTester} rather than in the annotation package.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public interface DatabaseTesterFactory
{
    /**
     * Returns a configured {@link IDatabaseTester}. dbUnit calls this method for each test, on
     * the one instance of the factory, possibly from several threads at once.
     *
     * @return The configured tester.
     * @throws Exception If creating the tester fails.
     */
    IDatabaseTester getDatabaseTester() throws Exception;
}
