/*
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

package org.dbunit;

import org.dbunit.operation.DatabaseOperation;

import java.io.File;
import java.io.FilenameFilter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
public class H2Environment extends DatabaseEnvironment
{
    public static final String USERNAME_DEFAULT = "sa";
    public static final String PASSWORD_DEFAULT = "";

    public H2Environment(DatabaseProfile profile) throws Exception
    {
        super(profile);
    }

    public static Connection createJdbcConnection(String databaseName)
            throws Exception
    {
        return createJdbcConnection(databaseName, USERNAME_DEFAULT,
                PASSWORD_DEFAULT);
    }

    public static Connection createJdbcConnection(String databaseName,
            String username, String password) throws Exception
    {
        Class.forName("org.h2.Driver");
        return DriverManager.getConnection("jdbc:h2:mem:" + databaseName,
                username, password);
    }

    @Override
    public void closeConnection() throws Exception
    {
        DatabaseOperation.DELETE_ALL.execute(getConnection(), getInitDataSet());
    }

    public static void shutdown(Connection connection) throws SQLException
    {
        DdlExecutor.executeSql(connection, "SHUTDOWN IMMEDIATELY");
    }

    public static void deleteFiles(final String filename)
    {
        deleteFiles(new File("."), filename);
    }

    public static void deleteFiles(File directory, final String filename)
    {
        File[] files = directory.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                if (name.indexOf(filename) != -1)
                {
                    return true;
                }
                return false;
            }
        });

        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];
            file.delete();
        }

    }
}
