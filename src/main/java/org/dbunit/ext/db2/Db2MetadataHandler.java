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
package org.dbunit.ext.db2;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.database.DefaultMetadataHandler;
import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Customized MetadataHandler for DB2 as match Columns of {@link DefaultMetadataHandler}
 * fails with a RuntimeException.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.7
 */
public class Db2MetadataHandler extends DefaultMetadataHandler {

    private static final Logger logger = LoggerFactory.getLogger(Db2MetadataHandler.class);

    public Db2MetadataHandler() {
        super();
    }

    /**
     * This method is overridden since - at least with DB2 driver db2jcc-9.5.jar - there is a
     * problem that the {@link DatabaseMetaData} does not return the same values for catalog and schema
     * like the columns {@link ResultSet} does. The debugging constellation is as follows
     * <pre>
     * catalog="BLA", catalogName=<null>
     * schema="BLA", schemaName="BLA"
     * </pre>
     * This problem is taken into account by this metadata handler.
     * 
     * {@inheritDoc}
     * @see org.dbunit.database.DefaultMetadataHandler#matches(java.sql.ResultSet, java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean)
     */
    public boolean matches(ResultSet columnsResultSet, String catalog,
            String schema, String table, String column, boolean caseSensitive)
            throws SQLException {
        if (logger.isTraceEnabled())
            logger.trace("matches(columnsResultSet={}, catalog={}, schema={},"
                    + " table={}, column={}, caseSensitive={}) - start",
                    new Object[] { columnsResultSet, catalog, schema, table,
                            column, Boolean.valueOf(caseSensitive) });

        String catalogName = columnsResultSet.getString(1);
        String schemaName = columnsResultSet.getString(2);
        String tableName = columnsResultSet.getString(3);
        String columnName = columnsResultSet.getString(4);

        if (logger.isDebugEnabled()) {
            logger
                    .debug(
                            "Comparing the following values using caseSensitive={} (searched<=>actual): "
                                    + "catalog: {}<=>{} schema: {}<=>{} table: {}<=>{} column: {}<=>{}",
                            new Object[] { Boolean.valueOf(caseSensitive),
                                    catalog, catalogName, schema, schemaName,
                                    table, tableName, column, columnName });
        }

        boolean areEqual = areEqualIgnoreBothNull(catalog, catalogName, caseSensitive)
                && areEqualIgnoreNull(schema, schemaName, caseSensitive)
                && areEqualIgnoreNull(table, tableName, caseSensitive)
                && areEqualIgnoreNull(column, columnName, caseSensitive);
        return areEqual;
    }

    private boolean areEqualIgnoreBothNull(String value1, String value2,
            boolean caseSensitive) {
        boolean areEqual = true;
        if (value1 != null && value2 != null) {
            if (value1.equals("") && value2.equals("")) {
                if (caseSensitive) {
                    areEqual = value1.equals(value2);
                } else {
                    areEqual = value1.equalsIgnoreCase(value2);
                }
            }
        }
        return areEqual;
    }

    private boolean areEqualIgnoreNull(String value1, String value2,
            boolean caseSensitive) {
        return SQLHelper.areEqualIgnoreNull(value1, value2, caseSensitive);
    }

}
