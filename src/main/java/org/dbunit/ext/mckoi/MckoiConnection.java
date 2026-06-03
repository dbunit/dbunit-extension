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

package org.dbunit.ext.mckoi;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;

import java.sql.Connection;

/**
 * Database connection for Mckoi that pre-configures all properties required to successfully
 * use dbunit with Mckoi.
 * 
 * @author Luigi Talamona (luigitalamona AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public class MckoiConnection extends DatabaseConnection
{

    public MckoiConnection(Connection connection, String schema) throws DatabaseUnitException
    {
        super(connection, schema);
        getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new MckoiDataTypeFactory());
    }
}
