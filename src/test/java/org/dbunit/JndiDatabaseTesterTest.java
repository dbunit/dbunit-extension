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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.database.CachingConnectionProvider;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JndiDatabaseTester} that need no naming context; the ones that do are in
 * {@link JndiDatabaseTesterIT}.
 *
 * @since 3.6.0
 */
class JndiDatabaseTesterTest
{
    private static final String LOOKUP_NAME = "java:comp/env/jdbc/dbunit";

    @Test
    void testIsReusingConnection_withoutProvider_returnsFalse()
    {
        final JndiDatabaseTester tester = new JndiDatabaseTester(LOOKUP_NAME);

        assertThat(tester.isReusingConnection())
                .as("Without a CachingConnectionProvider every call asks the DataSource for a"
                        + " connection.")
                .isFalse();
    }

    @Test
    void testIsReusingConnection_withProvider_returnsTrue()
    {
        final JndiDatabaseTester tester = new JndiDatabaseTester(null, LOOKUP_NAME, null,
                new CachingConnectionProvider());

        assertThat(tester.isReusingConnection())
                .as("With a CachingConnectionProvider calls reuse the cached connection.")
                .isTrue();
    }
}
