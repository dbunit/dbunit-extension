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

import org.dbunit.util.FileHelper;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
public class DerbyEnvironment extends DatabaseEnvironment
{

    public DerbyEnvironment(DatabaseProfile profile) throws Exception
    {
        super(profile, new Callable<Void>()
        {
            public Void call() throws Exception
            {
                // Delete the old database if exists before creating a new one
                // in "getConnection()"
                // The name of the db is specified in the profile.properties and
                // is created on the fly
                // when the connection is retrieved the first time
                FileHelper.deleteDirectory(new File("./target/derby_db"));

                return null;
            }
        });
    }
}
