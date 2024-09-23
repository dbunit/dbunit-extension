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

import java.util.Arrays;
import java.util.Comparator;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a ITable decorator that provide a sorted view of the decorated table.
 * This implementation does not keep a separate copy of the decorated table
 * data.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date: 2009-05-01 02:56:07 -0500 (Fri, 01 May 2009) $
 * @since Feb 19, 2003
 */
public class SortedTable extends AbstractTable
{
    private static final Logger logger =
            LoggerFactory.getLogger(SortedTable.class);

    private final ITable _table;
    private final Column[] _columns;
    private Integer[] _indexes;

    /**
     * The row comparator which is used for sorting
     */
    private Comparator rowComparator;

    /**
     * Sort the decorated table by specified columns order. Resulting table uses
     * column definitions from the specified table's metadata, not the specified
     * columns.
     *
     * @param table
     *            decorated table
     * @param columns
     *            columns to be used for sorting
     * @throws DataSetException
     */
    public SortedTable(final ITable table, final Column[] columns)
            throws DataSetException
    {
        this(table, columns, false);
    }

    /**
     * Sort the decorated table by specified columns order. Resulting table uses
     * column definitions from the specified columns, not the ones from the
     * specified table's metadata.
     *
     * @param table
     *            decorated table
     * @param columns
     *            columns to be used for sorting
     * @param useSpecifiedColumns
     *            true to use the column definitions specified by the columns
     *            parameter, false to use the column definitions from the
     *            specified table's metadata.
     * @throws DataSetException
     */
    public SortedTable(final ITable table, final Column[] columns,
            final boolean useSpecifiedColumns) throws DataSetException
    {
        _table = table;

        final Column[] validatedColumns = validateAndResolveColumns(columns);
        if (useSpecifiedColumns)
        {
            _columns = columns;
        } else
        {
            _columns = validatedColumns;
        }

        initialize();
    }

    /**
     * Sort the decorated table by specified columns order. Resulting table uses
     * column definitions from the specified table's metadata.
     *
     * @param table
     *            decorated table
     * @param columnNames
     *            names of columns to be used for sorting
     * @throws DataSetException
     */
    public SortedTable(final ITable table, final String[] columnNames)
            throws DataSetException
    {
        _table = table;
        _columns = validateAndResolveColumns(columnNames);
        initialize();
    }

    /**
     * Sort the decorated table by specified metadata columns order. All
     * metadata columns will be used.
     *
     * @param table
     *            The decorated table
     * @param metaData
     *            The metadata used to retrieve all columns which in turn are
     *            used for sorting the table
     * @throws DataSetException
     */
    public SortedTable(final ITable table, final ITableMetaData metaData)
            throws DataSetException
    {
        this(table, metaData.getColumns());
    }

    /**
     * Sort the decorated table by its own columns order which is defined by
     * {@link ITable#getTableMetaData()}. All table columns will be used.
     *
     * @param table
     *            The decorated table
     * @throws DataSetException
     */
    public SortedTable(final ITable table) throws DataSetException
    {
        this(table, table.getTableMetaData());
    }

    /**
     * Verifies that all given columns really exist in the current table and
     * returns the physical {@link Column} objects from the table.
     *
     * @param columns
     * @return
     * @throws DataSetException
     */
    private Column[] validateAndResolveColumns(final Column[] columns)
            throws DataSetException
    {
        final ITableMetaData tableMetaData = _table.getTableMetaData();
        final Column[] resultColumns =
                Columns.findColumnsByName(columns, tableMetaData);
        return resultColumns;
    }

    /**
     * Verifies that all given columns really exist in the current table and
     * returns the physical {@link Column} objects from the table.
     *
     * @param columnNames
     * @return
     * @throws DataSetException
     */
    private Column[] validateAndResolveColumns(final String[] columnNames)
            throws DataSetException
    {
        final ITableMetaData tableMetaData = _table.getTableMetaData();
        final Column[] resultColumns =
                Columns.findColumnsByName(columnNames, tableMetaData);
        return resultColumns;
    }

    private void initialize()
    {
        logger.debug("initialize() - start");

        // The default comparator is the one that sorts by string - for
        // backwards compatibility
        this.rowComparator =
                new RowComparatorByString(this._table, this._columns);
    }

    /**
     * @return The columns that are used for sorting the table
     */
    public Column[] getSortColumns()
    {
        return this._columns;
    }

    private int getOriginalRowIndex(final int row) throws DataSetException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("getOriginalRowIndex(row={}) - start",
                    Integer.toString(row));
        }

        if (_indexes == null)
        {
            final Integer[] indexes = new Integer[getRowCount()];
            for (int i = 0; i < indexes.length; i++)
            {
                indexes[i] = i;
            }

            try
            {
                Arrays.sort(indexes, rowComparator);
            } catch (final DatabaseUnitRuntimeException e)
            {
                throw (DataSetException) e.getCause();
            }

            _indexes = indexes;
        }

        return _indexes[row].intValue();
    }

    /**
     * Whether or not the comparable interface should be used of the compared
     * columns instead of the plain strings Default value is <code>false</code>
     * for backwards compatibility Set whether or not to use the Comparable
     * implementation of the corresponding column DataType for comparing values
     * or not. Default value is <code>false</code> which means that the old
     * string comparison is used. <br>
     *
     * @param useComparable
     * @since 2.3.0
     */
    public void setUseComparable(final boolean useComparable)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("setUseComparable(useComparable={}) - start",
                    Boolean.valueOf(useComparable));
        }

        if (useComparable)
        {
            setRowComparator(new RowComparator(this._table, this._columns));
        } else
        {
            setRowComparator(
                    new RowComparatorByString(this._table, this._columns));
        }
    }

    /**
     * Sets the comparator to be used for sorting the table rows.
     *
     * @param comparator
     *            that sorts the table rows
     * @since 2.4.2
     */
    public void setRowComparator(final Comparator comparator)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("setRowComparator(comparator={}) - start", comparator);
        }

        if (_indexes != null)
        {
            // TODO this is an ugly design to avoid increasing the number of
            // constructors from 4 to 8. To be discussed how to implement it the
            // best way.
            throw new IllegalStateException(
                    "Do not use this method after the table has been used (i.e. #getValue() has been called). "
                            + "Please invoke this method immediately after the intialization of this object.");
        }

        this.rowComparator = comparator;
    }

    // //////////////////////////////////////////////////////////////////////////
    // ITable interface

    @Override
    public ITableMetaData getTableMetaData()
    {
        logger.debug("getTableMetaData() - start");

        return _table.getTableMetaData();
    }

    @Override
    public int getRowCount()
    {
        logger.debug("getRowCount() - start");

        return _table.getRowCount();
    }

    @Override
    public Object getValue(final int row, final String columnName)
            throws DataSetException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("getValue(row={}, columnName={}) - start",
                    Integer.toString(row), columnName);
        }

        assertValidRowIndex(row);

        return _table.getValue(getOriginalRowIndex(row), columnName);
    }

    // //////////////////////////////////////////////////////////////////////////
    // Comparator interface

    /**
     * Abstract class for sorting the table rows of a given table in a specific
     * order
     */
    public static abstract class AbstractRowComparator implements Comparator
    {
        /**
         * Logger for this class
         */
        private final Logger logger =
                LoggerFactory.getLogger(AbstractRowComparator.class);
        private final ITable _table;
        private final Column[] _sortColumns;

        /**
         * @param table
         *            The wrapped table to be sorted
         * @param sortColumns
         *            The columns to be used for sorting in the given order
         */
        public AbstractRowComparator(final ITable table,
                final Column[] sortColumns)
        {
            this._table = table;
            this._sortColumns = sortColumns;
        }

        @Override
        public int compare(final Object o1, final Object o2)
        {
            logger.debug("compare(o1={}, o2={}) - start", o1, o2);

            final Integer i1 = (Integer) o1;
            final Integer i2 = (Integer) o2;

            try
            {
                for (int i = 0; i < _sortColumns.length; i++)
                {
                    final String columnName = _sortColumns[i].getColumnName();

                    final Object value1 =
                            _table.getValue(i1.intValue(), columnName);
                    final Object value2 =
                            _table.getValue(i2.intValue(), columnName);

                    if (value1 == null && value2 == null)
                    {
                        continue;
                    }

                    if (value1 == null && value2 != null)
                    {
                        return -1;
                    }

                    if (value1 != null && value2 == null)
                    {
                        return 1;
                    }

                    // Compare the two values with each other for sorting
                    final int result = compare(_sortColumns[i], value1, value2);

                    if (result != 0)
                    {
                        return result;
                    }
                }
            } catch (final DataSetException e)
            {
                throw new DatabaseUnitRuntimeException(e);
            }

            return 0;
        }

        /**
         * @param column
         *            The column to be compared
         * @param value1
         *            The first value of the given column
         * @param value2
         *            The second value of the given column
         * @return 0 if both values are considered equal.
         * @throws TypeCastException
         */
        protected abstract int compare(Column column, Object value1,
                Object value2) throws TypeCastException;

    }

    /**
     * Compares the rows with each other in order to sort them in the correct
     * order using the data type and the Comparable implementation the current
     * column has.
     */
    protected static class RowComparator extends AbstractRowComparator
    {
        /**
         * Logger for this class
         */
        private final Logger logger =
                LoggerFactory.getLogger(RowComparator.class);

        public RowComparator(final ITable table, final Column[] sortColumns)
        {
            super(table, sortColumns);
        }

        @Override
        protected int compare(final Column column, final Object value1,
                final Object value2) throws TypeCastException
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("compare(column={}, value1={}, value2={}) - start",
                        new Object[] {column, value1, value2});
            }

            final DataType dataType = column.getDataType();
            final int result = dataType.compare(value1, value2);
            return result;
        }

    }

    /**
     * Compares the rows with each other in order to sort them in the correct
     * order using the string value of both values for the comparison.
     */
    protected static class RowComparatorByString extends AbstractRowComparator
    {
        /**
         * Logger for this class
         */
        private final Logger logger =
                LoggerFactory.getLogger(RowComparatorByString.class);

        public RowComparatorByString(final ITable table,
                final Column[] sortColumns)
        {
            super(table, sortColumns);
        }

        @Override
        protected int compare(final Column column, final Object value1,
                final Object value2) throws TypeCastException
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("compare(column={}, value1={}, value2={}) - start",
                        new Object[] {column, value1, value2});
            }

            // Default behavior since ever
            final String stringValue1 = DataType.asString(value1);
            final String stringValue2 = DataType.asString(value2);
            final int result = stringValue1.compareTo(stringValue2);
            return result;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(2000);

        sb.append(getClass().getName()).append("[");
        sb.append("_columns=[").append(Arrays.toString(_columns)).append("], ");
        sb.append("_indexes=[").append(_indexes).append("], ");
        sb.append("_table=[").append(_table).append("]");
        sb.append("]");

        return sb.toString();
    }
}
