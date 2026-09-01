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

import org.dbunit.VerifyTableDefinition;

/**
 * A package-private top-level catalog class, deliberately declared outside
 * {@code org.dbunit.annotation.runtime} - unlike a nested fixture in that package,
 * reading this class's public field via reflection from {@code VerifyTableDefinitionCatalog}
 * genuinely requires {@code setAccessible(true)}, since the declaring class itself is not
 * public and the reader is in a different package.
 */
class CrossPackageVerifyTableCatalog
{
    public static final VerifyTableDefinition ACCOUNT =
            new VerifyTableDefinition("ACCOUNT", (String[]) null);
}
