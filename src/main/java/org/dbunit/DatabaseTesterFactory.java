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
 * Produces a configured {@link IDatabaseTester}.
 *
 * <p>{@link IDatabaseTester} implementations have no uniform no-arg constructor -
 * {@link JdbcDatabaseTester} needs a driver and URL, {@link DataSourceDatabaseTester} a
 * {@code DataSource}, {@link JndiDatabaseTester} a lookup name - so a factory is the mechanism
 * for {@code org.dbunit.annotation.DbUnitConfig#databaseTesterFactory()} to reflectively
 * instantiate (with a no-arg constructor of the factory itself) and ask for a ready-to-use
 * tester.
 *
 * <p>This is a general dbUnit concept, not specific to annotation-driven configuration, so it
 * lives beside {@link IDatabaseTester} rather than in the annotation package.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public interface DatabaseTesterFactory {
    /**
     * Creates and returns a configured {@link IDatabaseTester}.
     *
     * @return The configured tester.
     * @throws Exception if creating the tester fails.
     */
    IDatabaseTester createDatabaseTester() throws Exception;
}
