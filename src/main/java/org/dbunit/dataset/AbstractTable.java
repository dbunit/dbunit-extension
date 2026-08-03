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
package org.dbunit.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base implementation of {@link ITable}, providing common column-lookup
 * and value-formatting behavior for concrete table implementations.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public abstract class AbstractTable implements ITable {

    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(AbstractTable.class);

    /**
     * Validates that the given row index is within the bounds of this table.
     *
     * @param row the row index to validate.
     * @throws DataSetException if the row index is out of bounds.
     */
    protected void assertValidRowIndex(int row) throws DataSetException {
        if (logger.isDebugEnabled()) {
            logger.debug("assertValidRowIndex(row={}) - start", Integer
                    .toString(row));
        }

        assertValidRowIndex(row, getRowCount());
    }

    /**
     * Validates that the given row index is within the given row count.
     *
     * @param row the row index to validate.
     * @param rowCount the number of rows to validate against.
     * @throws DataSetException if the row index is out of bounds.
     */
    protected void assertValidRowIndex(int row, int rowCount)
            throws DataSetException {
        if (logger.isDebugEnabled()) {
            logger.debug("assertValidRowIndex(row={}, rowCount={}) - start",
                    Integer.toString(row), Integer.toString(rowCount));
        }

        if (row < 0) {
            throw new RowOutOfBoundsException(row + " < 0");
        }

        if (row >= rowCount) {
            throw new RowOutOfBoundsException(row + " >= " + rowCount);
        }
    }

    /**
     * Validates that the given column name exists in this table.
     *
     * @param columnName the column name to validate.
     * @throws DataSetException if no column with the given name exists in this table.
     */
    protected void assertValidColumn(String columnName) throws DataSetException {
        logger.debug("assertValidColumn(columnName={}) - start", columnName);

        ITableMetaData metaData = getTableMetaData();
        // Try to find the column in the metadata - if it cannot be found an
        // exception is thrown
        Columns.getColumnValidated(columnName, metaData.getColumns(), metaData
                .getTableName());
    }

    /**
     * Returns the index of the column with the given name.
     *
     * @param columnName the column name to look up.
     * @return the index of the column with the given name.
     * @throws DataSetException if no column with the given name exists in this table.
     */
    protected int getColumnIndex(String columnName) throws DataSetException {
        logger.debug("getColumnIndex(columnName={}) - start", columnName);

        ITableMetaData metaData = getTableMetaData();
        return metaData.getColumnIndex(columnName);
    }
}
