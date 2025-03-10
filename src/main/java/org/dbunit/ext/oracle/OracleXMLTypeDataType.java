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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Types;

import org.dbunit.dataset.datatype.BlobDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;

/**
 *
 * TODO UnitTests are completely missing
 *
 * @author Phil Barr
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class OracleXMLTypeDataType extends BlobDataType
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    OracleXMLTypeDataType()
    {
        super("SQLXML", Types.SQLXML);
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        byte[] data = null;
        final OracleResultSet oracleResultSet =
                resultSet.unwrap(OracleResultSet.class);
        final SQLXML sqlXml = oracleResultSet.getSQLXML(column);
        if (sqlXml != null)
        {
            data = sqlXml.getString().getBytes();
        }

        // return the byte data (using typeCast to cast it to Base64 notation)
        final Object typeCast = typeCast(data);
        final String string =
                typeCast == null ? null : new String((byte[]) typeCast);
        log.trace("getSqlValue: column={}, data={}, typeCast={}, string={}",
                column, data, typeCast, string);

        return typeCast;
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        final OraclePreparedStatement oraclePreparedStatement =
                statement.unwrap(OraclePreparedStatement.class);
        final SQLXML sqlXmlValue =
                oraclePreparedStatement.getConnection().createSQLXML();

        // XML document in the parameter is Base64 encoded (it is entered in XML
        // parameter)
        final byte[] typeCast = (byte[]) typeCast(value);
        final String string = new String(typeCast);

        log.trace("setSqlValue: column={}, value={}, typeCast={}, string={}",
                column, value, typeCast, string);

        sqlXmlValue.setString(string);
        oraclePreparedStatement.setSQLXML(column, sqlXmlValue);
    }

    @Override
    public String getSqlTypeName()
    {
        return "SYS.XMLTYPE";
    }
}
