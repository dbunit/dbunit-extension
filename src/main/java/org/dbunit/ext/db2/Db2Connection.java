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

package org.dbunit.ext.db2;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;

import java.sql.Connection;

/**
 * Database connection for DB2 that pre-configures all properties required to successfully
 * use dbunit with DB2.
 * 
 * @author Federico Spinazzi
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.5.5 (Jul 17, 2003)
 */
public class Db2Connection extends DatabaseConnection
{

    /**
     * Creates a DB2 connection, pre-configuring the DB2-specific data type factory and
     * metadata handler.
     *
     * @param connection the adapted JDBC connection.
     * @param schema the database schema.
     * @throws DatabaseUnitException if the connection cannot be adapted.
     */
    public Db2Connection(Connection connection, String schema) throws DatabaseUnitException
    {
        super(connection, schema);
        getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new Db2DataTypeFactory());
        getConfig().setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER, 
                new Db2MetadataHandler());
    }
}