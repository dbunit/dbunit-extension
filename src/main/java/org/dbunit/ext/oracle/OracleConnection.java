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
package org.dbunit.ext.oracle;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;

import java.sql.Connection;
import java.util.Locale;

/**
 * {@link DatabaseConnection} specialization that configures Oracle-specific
 * connection behavior.
 *
 * @author manuel.laflamme
 * @since Sep 3, 2003
 * @version $Revision$
 */
public class OracleConnection extends DatabaseConnection
{
    /**
     * Creates a oracle connection. Beware that the given schema is passed in to the parent class
     * as "upper case" string.
     * @param connection
     * @param schema The schema name
     * @throws DatabaseUnitException
     */
    public OracleConnection(Connection connection, String schema) throws DatabaseUnitException
    {
        super(connection, schema != null ? schema.toUpperCase(Locale.ENGLISH) : null);
        getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new OracleDataTypeFactory());
    }
}
