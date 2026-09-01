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
 * <p>An {@code org.dbunit.annotation} provider or factory class: it is named on an annotation
 * attribute by {@link Class}, instantiated once through its own public no-arg constructor, and
 * asked for its one value - here {@link #getDatabaseTester()}. The other members of this family
 * are {@link org.dbunit.util.fileloader.DataSetPathsProvider},
 * {@link org.dbunit.database.DatabaseConfigPropertiesProvider}, and
 * {@link VerifyTableDefinitionsProvider}. A factory rather than a bare provider because
 * {@link IDatabaseTester} implementations have no uniform no-arg constructor -
 * {@link JdbcDatabaseTester} needs a driver and URL, {@link DataSourceDatabaseTester} a
 * {@code DataSource}, {@link JndiDatabaseTester} a lookup name.
 *
 * <p>This is a general dbUnit concept, not specific to annotation-driven configuration, so it
 * lives beside {@link IDatabaseTester} rather than in the annotation package.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see org.dbunit.util.fileloader.DataSetPathsProvider
 * @see org.dbunit.database.DatabaseConfigPropertiesProvider
 * @see VerifyTableDefinitionsProvider
 */
public interface DatabaseTesterFactory
{
    /**
     * Returns a configured {@link IDatabaseTester}.
     *
     * @return The configured tester.
     * @throws Exception If creating the tester fails.
     */
    IDatabaseTester getDatabaseTester() throws Exception;
}
