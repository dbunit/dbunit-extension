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
package org.dbunit.database;

import java.util.Properties;

import org.dbunit.annotation.DbUnitConfig;

/**
 * Supplies a shared set of {@link DatabaseConfig} properties for {@link DbUnitConfig#propertiesProvider()},
 * an implementing class named there is reflectively instantiated with its no-arg constructor
 * and asked for the properties to apply.
 *
 * <p>Java annotations cannot reference an object constant directly (see
 * {@link DbUnitConfig#propertiesProvider()}), so a provider class is the mechanism for
 * reusing one {@link Properties} instance across several test classes; {@link DbUnitConfig#properties()}
 * remains the inline alternative for properties specific to one class or method.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitConfig
 */
public interface DatabaseConfigPropertiesProvider {
    /**
     * Returns the properties to apply to the test's {@link DatabaseConfig}.
     *
     * @return The properties, in the form accepted by
     *         {@link DatabaseConfig#setPropertiesByString(Properties)}.
     */
    Properties getProperties();
}
