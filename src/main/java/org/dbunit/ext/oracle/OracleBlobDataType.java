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

import oracle.sql.BLOB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.datatype.BlobDataType;
import org.dbunit.dataset.datatype.TypeCastException;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Feb 2, 2004
 */
public class OracleBlobDataType extends BlobDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(OracleBlobDataType.class);

    public Object getSqlValue(int column, ResultSet resultSet) throws SQLException, TypeCastException
    {
    	logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        return typeCast(resultSet.getBlob(column));
    }

    public void setSqlValue(Object value, int column, PreparedStatement statement)
            throws SQLException, TypeCastException
    {
    	logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
    		value, column, statement);

        statement.setObject(column, getBlob(value, statement.getConnection()));
    }

    private Object getBlob(Object value, Connection connection) throws TypeCastException
    {
        logger.debug("getBlob(value={}, connection={}) - start", value, connection);

        oracle.sql.BLOB tempBlob = null;
        try
        {
            tempBlob = oracle.sql.BLOB.createTemporary(connection, true, oracle.sql.BLOB.DURATION_SESSION);
            tempBlob.open(oracle.sql.BLOB.MODE_READWRITE);
            OutputStream tempBlobOutputStream = tempBlob.getBinaryOutputStream();

            // Write the data into the temporary BLOB
            tempBlobOutputStream.write((byte[])typeCast(value));

            // Flush and close the stream
            tempBlobOutputStream.flush();
            tempBlobOutputStream.close();

            // Close the temporary Blob
            tempBlob.close();
        }
        catch (SQLException e)
        {
            // JH_TODO: shouldn't freeTemporary be called in finally {} ?
            // It wasn't done like that in the original reflection-styled DbUnit code.
            freeTemporaryBlob(tempBlob);
            throw new TypeCastException(value, this, e);
        }
        catch (IOException e)
        {
            freeTemporaryBlob(tempBlob);
            throw new TypeCastException(value, this, e);
        }

        return tempBlob;
    }


    private void freeTemporaryBlob(oracle.sql.BLOB tempBlob) throws TypeCastException
    {
        logger.debug("freeTemporaryBlob(tempBlob={}) - start", tempBlob);

        if (tempBlob == null)
        {
            return;
        }

        try
        {
            tempBlob.freeTemporary();
        }
        catch (SQLException e)
        {
            throw new TypeCastException("Error freeing Oracle BLOB", e);
        }
    }

}
