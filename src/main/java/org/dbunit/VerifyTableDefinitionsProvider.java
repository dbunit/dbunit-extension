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

import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;

/**
 * Computes a catalog's {@link VerifyTableDefinition}s for {@link DbUnitConfig#verifyDefinitions()}
 * or {@link DbUnitExpected#verifyDefinitions()}, for the case where the definitions must be
 * computed rather than declared as constants.
 *
 * <p>A catalog class named on either attribute is checked for this interface first: if it
 * implements {@link VerifyTableDefinitionsProvider}, it is reflectively instantiated with its
 * no-arg constructor and asked for the definitions; otherwise the catalog's
 * {@code public static final VerifyTableDefinition} fields are read directly and indexed by
 * {@link VerifyTableDefinition#getTableName()}. A plain constants class - no interface, no
 * boilerplate - is the common case; implementing this interface is the escape hatch for a
 * catalog whose definitions cannot be plain constants.
 *
 * <p>The catalog machinery reflectively instantiating this interface caches an
 * implementation's {@link #getVerifyTableDefinitions()} result for the JVM's entire lifetime,
 * keyed by the exact combination of catalog classes named together - see
 * {@code org.dbunit.annotation.runtime.VerifyTableDefinitionCatalog#forClasses}. An
 * implementation is therefore resolved once and reused by every later test naming the same
 * combination, never called again; it must be a pure function of the catalog class itself,
 * with no dependency on mutable external state that could legitimately differ between calls.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitConfig
 * @see DbUnitExpected
 */
public interface VerifyTableDefinitionsProvider
{
    /**
     * Returns the catalog's table definitions.
     *
     * @return The table definitions.
     */
    VerifyTableDefinition[] getVerifyTableDefinitions();
}
