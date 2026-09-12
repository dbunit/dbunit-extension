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
package org.dbunit.spring;

import org.dbunit.DatabaseProfile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A {@code DataSource} that opens a new connection to the integration-test database of a
 * {@link DatabaseProfile} for every request, so a tester built on it holds no connection that
 * outlives the test it runs in - unlike one built on the shared connection of
 * {@code DatabaseEnvironment}, which a test closes behind the back of a context Spring caches.
 */
final class ProfileDataSource extends DriverManagerDataSource
{
    /**
     * Creates the data source.
     *
     * @param profile The profile of the database to connect to.
     */
    ProfileDataSource(final DatabaseProfile profile)
    {
        super(profile.getConnectionUrl(), profile.getUser(), profile.getPassword());
        setDriverClassName(profile.getDriverClass());
    }
}
