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
package org.dbunit.util.fileloader;

import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;

/**
 * Supplies a shared list of dataset classpath resource paths for {@link DbUnitPrep#provider()}
 * or {@link DbUnitExpected#provider()}.
 *
 * <p>An {@code org.dbunit.annotation} provider or factory class: it is named on an annotation
 * attribute by {@link Class}, instantiated once through its own public no-arg constructor, and
 * asked for its one value - here {@link #getDataSetPaths()}. The other members of this family
 * are {@link org.dbunit.database.DatabaseConfigPropertiesProvider},
 * {@link org.dbunit.VerifyTableDefinitionsProvider}, and {@link org.dbunit.DatabaseTesterFactory}.
 *
 * <p>Java annotations cannot reference a {@code String[]} constant directly (see
 * {@link DbUnitPrep#value()}), so a provider class is the mechanism for reusing one path list
 * across several test classes; {@link DbUnitPrep#value()}/{@link DbUnitExpected#value()} remain
 * the inline alternative. Setting both is rejected.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitPrep
 * @see DbUnitExpected
 * @see org.dbunit.database.DatabaseConfigPropertiesProvider
 * @see org.dbunit.VerifyTableDefinitionsProvider
 * @see org.dbunit.DatabaseTesterFactory
 */
public interface DataSetPathsProvider
{
    /**
     * Returns the dataset classpath resource paths to load.
     *
     * @return The dataset paths, resolved the same way as {@link DbUnitPrep#value()} or
     *         {@link DbUnitExpected#value()}.
     */
    String[] getDataSetPaths();
}
