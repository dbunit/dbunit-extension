/*
 * DatabaseConnectionTest.java   Mar 26, 2002
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

import org.dbunit.DatabaseProfile;
import org.dbunit.IDatabaseTester;
import org.dbunit.PropertiesBasedJdbcDatabaseTester;

/**
 * @author Andres Almiray (aalmiray@users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public class DefaultDatabaseTesterConnectionIT
        extends AbstractDatabaseTesterConnectionIT
{
    private PropertiesBasedJdbcDatabaseTester databaseTester;

    @Override
    protected IDatabaseTester getDatabaseTester() throws Exception
    {
        if (databaseTester == null)
        {
            final DatabaseProfile profile = getEnvironment().getProfile();
            System.setProperty(
                    PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS,
                    profile.getDriverClass());
            System.setProperty(
                    PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL,
                    profile.getConnectionUrl());
            System.setProperty(
                    PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME,
                    profile.getUser());
            System.setProperty(
                    PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD,
                    profile.getPassword());
            System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_SCHEMA,
                    profile.getSchema());
            databaseTester = new PropertiesBasedJdbcDatabaseTester();
        }
        return databaseTester;
    }
}
