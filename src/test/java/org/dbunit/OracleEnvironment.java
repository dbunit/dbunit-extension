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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.ext.oracle.OracleDataTypeFactory;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since May 2, 2002
 */
public class OracleEnvironment extends DatabaseEnvironment
{
    public OracleEnvironment(DatabaseProfile profile) throws Exception
    {
        super(profile);
    }

    protected void setupDatabaseConfig(DatabaseConfig config)
    {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new OracleDataTypeFactory());
    }

    public IDataSet getInitDataSet() throws Exception
    {
        ITable[] extraTables =
                {new DefaultTable("CLOB_TABLE"), new DefaultTable("BLOB_TABLE"),
                        new DefaultTable("SDO_GEOMETRY_TABLE"),
                        new DefaultTable("XML_TYPE_TABLE"),};

        return new CompositeDataSet(super.getInitDataSet(),
                new DefaultDataSet(extraTables));
    }
}
