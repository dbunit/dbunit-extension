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

package org.dbunit.database.statement;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;

import java.sql.SQLException;

/**
 * A batched, parameterized JDBC statement to which typed column values are bound
 * before execution.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 15, 2002
 */
public interface IPreparedBatchStatement
{
    /**
     * Binds the given value, cast using the given data type, to the next parameter of the statement.
     *
     * @param value the value to bind.
     * @param dataType the data type used to cast the value.
     * @throws TypeCastException if the value cannot be cast to the given data type.
     * @throws SQLException if binding the value to the statement fails.
     */
    void addValue(Object value, DataType dataType) throws TypeCastException,
            SQLException;

    /**
     * Adds the currently bound parameters as a new row to the batch.
     *
     * @throws SQLException if adding the batch row fails.
     */
    void addBatch() throws SQLException;

    /**
     * Executes all batched rows.
     *
     * @return the number of rows affected by each batched statement.
     * @throws SQLException if executing the batch fails.
     */
    int executeBatch() throws SQLException;

    /**
     * Clears all batched rows.
     *
     * @throws SQLException if clearing the batch fails.
     */
    void clearBatch() throws SQLException;

    /**
     * Closes this statement.
     *
     * @throws SQLException if closing the statement fails.
     */
    void close() throws SQLException;

}




