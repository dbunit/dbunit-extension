/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import org.dbunit.dataset.ITable;

/**
 * Value object to hold the difference of a single data cell found while
 * comparing data.
 * <p>
 * Inspired by the XMLUnit framework.
 * </p>
 *
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 * @since 2.6.0 added failMessage
 */
public class Difference
{
    private ITable expectedTable;
    private ITable actualTable;
    private int rowIndex;
    private String columnName;
    private Object expectedValue;
    private Object actualValue;
    private String failMessage;

    /**
     * Creates a difference with no fail message.
     *
     * @param expectedTable the table containing the expected results.
     * @param actualTable the table containing the actual results.
     * @param rowIndex the row index of the differing cell.
     * @param columnName the name of the differing column.
     * @param expectedValue the expected cell value.
     * @param actualValue the actual cell value.
     */
    public Difference(final ITable expectedTable, final ITable actualTable,
            final int rowIndex, final String columnName,
            final Object expectedValue, final Object actualValue)
    {
        this(expectedTable, actualTable, rowIndex, columnName, expectedValue,
                actualValue, "");
    }

    /**
     * Creates a difference with the given fail message.
     *
     * @param expectedTable the table containing the expected results.
     * @param actualTable the table containing the actual results.
     * @param rowIndex the row index of the differing cell.
     * @param columnName the name of the differing column.
     * @param expectedValue the expected cell value.
     * @param actualValue the actual cell value.
     * @param failMessage the comparison failure message.
     */
    public Difference(final ITable expectedTable, final ITable actualTable,
            final int rowIndex, final String columnName,
            final Object expectedValue, final Object actualValue,
            final String failMessage)
    {
        this.expectedTable = expectedTable;
        this.actualTable = actualTable;
        this.rowIndex = rowIndex;
        this.columnName = columnName;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.failMessage = failMessage;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("expectedTable=").append(expectedTable);
        sb.append(", actualTable=").append(actualTable);
        sb.append(", rowIndex=").append(rowIndex);
        sb.append(", columnName=").append(columnName);
        sb.append(", expectedValue=").append(expectedValue);
        sb.append(", actualValue=").append(actualValue);
        sb.append(", failMessage=").append(failMessage);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns the table containing the expected results.
     * @return the table containing the expected results.
     */
    public ITable getExpectedTable()
    {
        return expectedTable;
    }

    /**
     * Returns the table containing the actual results.
     * @return the table containing the actual results.
     */
    public ITable getActualTable()
    {
        return actualTable;
    }

    /**
     * Returns the row index of the differing cell.
     * @return the row index of the differing cell.
     */
    public int getRowIndex()
    {
        return rowIndex;
    }

    /**
     * Returns the name of the differing column.
     * @return the name of the differing column.
     */
    public String getColumnName()
    {
        return columnName;
    }

    /**
     * Returns the expected cell value.
     * @return the expected cell value.
     */
    public Object getExpectedValue()
    {
        return expectedValue;
    }

    /**
     * Returns the actual cell value.
     * @return the actual cell value.
     */
    public Object getActualValue()
    {
        return actualValue;
    }

    /**
     * Returns the comparison failure message.
     * @return the comparison failure message.
     */
    public String getFailMessage()
    {
        return failMessage;
    }

    /**
     * Sets the comparison failure message.
     * @param failMessage the comparison failure message.
     */
    public void setFailMessage(final String failMessage)
    {
        this.failMessage = failMessage;
    }
}
