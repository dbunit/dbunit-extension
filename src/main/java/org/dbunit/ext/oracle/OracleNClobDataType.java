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

import oracle.jdbc.OraclePreparedStatement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.dbunit.dataset.datatype.TypeCastException;

/**
 * NCLOB handler
 * @author cris.daniluk
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since May 3, 2005
 */
public class OracleNClobDataType extends OracleClobDataType {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(OracleNClobDataType.class);

    public void setSqlValue(Object value, int column, PreparedStatement statement)
            throws SQLException, TypeCastException
    {
    	logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
    		value, column, statement);

        OraclePreparedStatement oraclePreparedStatement = statement.unwrap(OraclePreparedStatement.class);
        oraclePreparedStatement.setFormOfUse(column, OraclePreparedStatement.FORM_NCHAR);
        statement.setObject(column, getClob(value, statement.getConnection()));
    }

}
