/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.ext.mariadb.MariaDbDataTypeFactory;
import org.dbunit.ext.mysql.MySqlMetadataHandler;

/**
 * @author Jeff Jensen (adapted from John Hurst: MySqlEnvironment)
 * @since 3.5.0
 */
public class MariaDbEnvironment extends DatabaseEnvironment
{
    public MariaDbEnvironment(DatabaseProfile profile) throws Exception
    {
        super(profile);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Also registers {@link MySqlMetadataHandler}: unlike MySQL Connector/J
     * (whose {@code nullCatalogMeansCurrent} default restricts an unfiltered
     * {@code DatabaseMetaData#getTables()} call to the current database),
     * MariaDB Connector/J returns every catalog's tables - including
     * {@code information_schema}/{@code performance_schema} - unless the
     * schema is passed as the JDBC catalog argument, which is exactly what
     * {@link MySqlMetadataHandler} does.
     */
    @Override
    protected void setupDatabaseConfig(DatabaseConfig config)
    {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new MariaDbDataTypeFactory());
        config.setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER,
                new MySqlMetadataHandler());
    }

    /**
     * Preserve case for MariaDB
     *
     * @see DatabaseEnvironment#convertString(String)
     */
    @Override
    public String convertString(String str)
    {
        return str;
    }

}
