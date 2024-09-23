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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.dbunit.dataset.filter.IColumnFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class exclusively provides static methods that operate on {@link Column} objects.
 * 
 * @author gommma
 * @version $Revision$ 
 * @since 2.3.0
 */
public class Columns 
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Columns.class);

    private static final ColumnComparator COLUMN_COMPARATOR = new ColumnComparator();

    private static final Column[] EMPTY_COLUMNS = new Column[0];

    
    private Columns()
    {
    }
    
    
	/**
     * Search and return the {@link Column}s from the specified column array that
     * match one of the given <code>columnNames</code>.
     * <br>
     * Note that this method has a bad performance compared to {@link #findColumnsByName(String[], ITableMetaData)}
     * because it iterates over all columns.
     * 
	 * @param columnNames the names of the columns to search.
	 * @param columns the array of columns in which the <code>columnNames</code> will be searched.
	 * @return the column array which is empty if no column has been found or no 
	 * column names have been given
	 * @see #findColumnsByName(String[], ITableMetaData)
	 */
	public static Column[] getColumns(String[] columnNames, Column[] columns) {
    	if (logger.isDebugEnabled())
    		logger.debug("getColumns(columnNames={}, columns={}) - start",
    				columnNames, columns);

    	if (columnNames == null || columnNames.length == 0)
        {
            return EMPTY_COLUMNS;
        }

        List resultList = new ArrayList();
        for (int i = 0; i < columnNames.length; i++)
        {
            Column column = Columns.getColumn(columnNames[i], columns);
            if (column != null)
            {
                resultList.add(column);
            }
        }

        return (Column[])resultList.toArray(new Column[0]);
	}

    /**
     * Searches for the given <code>columns</code> using only the {@link Column#getColumnName()} 
     * in the given <code>tableMetaData</code>
     * @param columnNames The column names that are searched in the given table metadata
     * @param tableMetaData The table metadata in which the columns are searched by name
     * @return The column objects from the given <code>tableMetaData</code>
     * @throws NoSuchColumnException if the given column has not been found
     * @throws DataSetException if something goes wrong when trying to retrieve the columns
     */
    public static Column[] findColumnsByName(String[] columnNames,
            ITableMetaData tableMetaData) 
    throws NoSuchColumnException, DataSetException 
    {
        logger.debug("findColumnsByName(columnNames={}, tableMetaData={}) - start", columnNames, tableMetaData);

        Column[] resultColumns = new Column[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) 
        {
            String sortColumn = columnNames[i];
            int colIndex = tableMetaData.getColumnIndex(sortColumn);
            resultColumns[i] = tableMetaData.getColumns()[colIndex];            
        }
        return resultColumns;
    }

    /**
     * Searches for the given <code>columns</code> using only the {@link Column#getColumnName()} 
     * in the given <code>tableMetaData</code>
     * @param columns The columns whose names are searched in the given table metadata
     * @param tableMetaData The table metadata in which the columns are searched by name
     * @return The column objects from the given <code>tableMetaData</code>
     * @throws NoSuchColumnException if the given column has not been found
     * @throws DataSetException if something goes wrong when trying to retrieve the columns
     */
    public static Column[] findColumnsByName(Column[] columns,
            ITableMetaData tableMetaData) 
    throws NoSuchColumnException, DataSetException 
    {
        logger.debug("findColumnsByName(columns={}, tableMetaData={}) - start", columns, tableMetaData);

        Column[] resultColumns = new Column[columns.length];
        for (int i = 0; i < columns.length; i++) 
        {
            Column sortColumn = columns[i];
            int colIndex = tableMetaData.getColumnIndex(sortColumn.getColumnName());
            resultColumns[i] = tableMetaData.getColumns()[colIndex];            
        }
        return resultColumns;
    }

    /**
     * Search and return the specified column from the specified column array.
     * <br>
     * Note that this method has a bad performance compared to {@link ITableMetaData#getColumnIndex(String)}
     * because it iterates over all columns.
     *
     * @param columnName the name of the column to search.
	 * @param columns the array of columns in which the <code>columnName</code> will be searched.
     * @return the column or <code>null</code> if the column is not found
     */
    public static Column getColumn(String columnName, Column[] columns)
    {
        logger.debug("getColumn(columnName={}, columns={}) - start", columnName, columns);

        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            if (columnName.equalsIgnoreCase(columns[i].getColumnName()))
            {
                return column;
            }
        }

        return null;
    }

    /**
     * Search and return the specified column from the specified column array.
     *
     * @param columnName the name of the column to search.
	 * @param columns the array of columns in which the <code>columnName</code> will be searched.
     * @param tableName The name of the table to which the column array belongs - 
     * only needed for the exception message in case of a validation failure
     * @return the valid column
     * @throws NoSuchColumnException If no column exists with the given name
     */
    public static Column getColumnValidated(String columnName, Column[] columns, String tableName) 
    throws NoSuchColumnException
    {
        if (logger.isDebugEnabled())
            logger.debug("getColumn(columnName={}, columns={}, tableName={}) - start", 
                columnName, columns, tableName);

        Column column = Columns.getColumn(columnName, columns);
        if(column==null)
        {
            throw new NoSuchColumnException(tableName, columnName);
        }
        
        return column;
    }

    /**
     * Search and return the columns from the specified column array which are
     * accepted by the given {@link IColumnFilter}.
     * @param tableName The name of the table which is needed for the filter invocation
     * @param columns All available columns to which the filter will be applied
     * @param columnFilter The column filter that is applied to the given <code>columns</code>
     * @return The columns that are accepted by the given filter
     */
    public static Column[] getColumns(String tableName, Column[] columns,
            IColumnFilter columnFilter)
    {
    	if (logger.isDebugEnabled())
    		logger.debug("getColumns(tableName={}, columns={}, columnFilter={}) - start",
    				tableName, columns, columnFilter);

        List resultList = new ArrayList();
        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            if (columnFilter.accept(tableName, column))
            {
                resultList.add(column);
            }
        }

        return (Column[])resultList.toArray(new Column[0]);
    }

    /**
     * Returns a sorted array of column objects
     * 
     * @param metaData The metaData needed to get the columns to be sorted
     * @return The columns sorted by their column names, ignoring the case of the column names
     * @throws DataSetException
     */
    public static Column[] getSortedColumns(ITableMetaData metaData)
    throws DataSetException
    {
        logger.debug("getSortedColumns(metaData={}) - start", metaData);

        Column[] columns = metaData.getColumns();
        Column[] sortColumns = new Column[columns.length];
        System.arraycopy(columns, 0, sortColumns, 0, columns.length);
        Arrays.sort(sortColumns, COLUMN_COMPARATOR);
        return sortColumns;
    }

    /**
     * Returns the names of the given column objects as string array
     * @param columns The column objects
     * @return The names of the given column objects
     * @since 2.4
     */
    public static String[] getColumnNames(Column[] columns) 
    {
        String[] result = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            result[i] = columns[i].getColumnName();
        }
        return result;
    }

    /**
     * Creates a pretty string representation of the given column names
     * @param columns The columns to be formatted
     * @return The string representation of the given column names
     */
    public static String getColumnNamesAsString(Column[] columns)
    {
        logger.debug("getColumnNamesAsString(columns={}) - start", columns);

        String[] names = new String[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            names[i] = column.getColumnName();
        }
        return Arrays.asList(names).toString();
    }

    /**
     * Merges the two arrays of columns so that all of the columns are available in the result array.
     * The first array is considered as master and if a column with a specific name is available in 
     * both arrays the one from the first array is used.
     * @param referenceColumns reference columns treated as master columns during the merge
     * @param columnsToMerge potentially new columns to be merged if they do not yet exist in the referenceColumns 
     * @return Array of merged columns
     */
    public static Column[] mergeColumnsByName(Column[] referenceColumns, Column[] columnsToMerge) {
        logger.debug("mergeColumnsByName(referenceColumns={}, columnsToMerge={}) - start", referenceColumns, columnsToMerge);

        List resultList = new ArrayList(Arrays.asList(referenceColumns));
        List columnsToMergeNotInRefList = new ArrayList(Arrays.asList(columnsToMerge));
        
        // All columns that exist in the referenceColumns
        for (int i = 0; i < referenceColumns.length; i++) {
            Column refColumn = referenceColumns[i];
            for (int k = 0; k < columnsToMerge.length; k++) {
                Column columnToMerge = columnsToMerge[k];
                // Check if this colToMerge exists in the refColumn
                if(columnToMerge.getColumnName().equals(refColumn.getColumnName())) {
                    // We found the column in the refColumns - so no candidate for adding to the result list
                    columnsToMergeNotInRefList.remove(columnToMerge);
                    break;
                }
            }
        }
        
        // Add all "columnsToMerge" that have not been found in the referenceColumnList
        resultList.addAll(columnsToMergeNotInRefList);
        return (Column[]) resultList.toArray(new Column[]{});
    }

    
	/**
	 * Returns the column difference of the two given {@link ITableMetaData} objects
	 * @param expectedMetaData
	 * @param actualMetaData
	 * @return The columns that differ in the both given {@link ITableMetaData} objects
	 * @throws DataSetException
	 */
	public static ColumnDiff getColumnDiff(ITableMetaData expectedMetaData,
			ITableMetaData actualMetaData) 
	throws DataSetException 
	{
		return new ColumnDiff(expectedMetaData, actualMetaData);
	}
    

	
    //  ColumnComparator class
    private static class ColumnComparator implements Comparator
    {
        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(ColumnComparator.class);

        /**
         * Compare columns by name ignoring case
         * @see java.util.Comparator#compare(T, T)
         */
        public int compare(Object o1, Object o2)
        {
            logger.debug("compare(o1={}, o2={}) - start", o1, o2);

            Column column1 = (Column)o1;
            Column column2 = (Column)o2;

            String columnName1 = column1.getColumnName();
            String columnName2 = column2.getColumnName();
            return columnName1.compareToIgnoreCase(columnName2);
        }
    }

    /**
     * Describes the {@link Column}s that are different in two tables.
     * @author gommma
     * @version $Revision$
     * @since 2.3.0
     */
    public static class ColumnDiff
    {
        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(ColumnDiff.class);
        /**
         * String message that is returned when no difference has been found in the compared columns
         */
		private static final String NO_DIFFERENCE = "no difference found";
    	
    	/**
    	 * The columns that exist in the expected result but not in the actual
    	 */
    	private Column[] expected; 
    	/**
    	 * The columns that exist in the actual result but not in the expected
    	 */
    	private Column[] actual;
    	private ITableMetaData expectedMetaData;
    	private ITableMetaData actualMetaData;
    	
    	/**
    	 * Creates the difference between the two metadata's columns
    	 * @param expectedMetaData The metadata of the expected results table
    	 * @param actualMetaData The metadata of the actual results table
    	 * @throws DataSetException
    	 */
    	public ColumnDiff(ITableMetaData expectedMetaData,
				ITableMetaData actualMetaData) 
    	throws DataSetException 
		{
    		if (expectedMetaData == null) {
				throw new NullPointerException(
						"The parameter 'expectedMetaData' must not be null");
			}
    		if (actualMetaData == null) {
				throw new NullPointerException(
						"The parameter 'actualMetaData' must not be null");
			}
    		
    		this.expectedMetaData = expectedMetaData;
    		this.actualMetaData = actualMetaData;
    		
    		Column[] allExpectedCols = expectedMetaData.getColumns();
    		Column[] allActualCols = actualMetaData.getColumns();
    		
    		// Get the columns that are missing on the actual side (walk through actual 
    		// columns and look for them in the expected metadata)
    		this.actual = findMissingColumnsIn(expectedMetaData, allActualCols);
    		// Get the columns that are missing on the expected side (walk through expected 
    		// columns and look for them in the actual metadata)
    		this.expected = findMissingColumnsIn(actualMetaData, allExpectedCols);
		}
    	
    	/**
    	 * Searches and returns all columns that are missing in the given {@link ITableMetaData} object
    	 * @param metaDataToCheck The {@link ITableMetaData} in which the given columns should be searched
    	 * @param columnsToSearch The columns to be searched in the given {@link ITableMetaData}
    	 * @return Those {@link Column}s out of the columnsToSearch that have not been found in metaDataToCheck
    	 * @throws DataSetException 
    	 */
    	private Column[] findMissingColumnsIn(ITableMetaData metaDataToCheck,
				Column[] columnsToSearch) throws DataSetException 
    	{
    		logger.debug("findMissingColumnsIn(metaDataToCheck={}, columnsToSearch={})", metaDataToCheck, columnsToSearch);
    		
    		List columnsNotFound = new ArrayList();
    		for (int i = 0; i < columnsToSearch.length; i++) {
    			try {
    				metaDataToCheck.getColumnIndex(columnsToSearch[i].getColumnName());
    			}
    			catch(NoSuchColumnException e) {
    				columnsNotFound.add(columnsToSearch[i]);
    			}
			}
    		
    		Column[] result = (Column[]) columnsNotFound.toArray(new Column[]{});
    		return result;
		}

    	/**
    	 * @return <code>true</code> if there is a difference in the columns given in the constructor
    	 */
    	public boolean hasDifference()
    	{
    		return this.expected.length > 0 || this.actual.length > 0;
    	}

		/**
    	 * @return The columns that exist in the expected result but not in the actual
    	 */
    	public Column[] getExpected() {
			return expected;
		}

		/**
		 * @return The columns that exist in the actual result but not in the expected
		 */
		public Column[] getActual() {
			return actual;
		}

		/**
		 * @return The value of {@link #getExpected()} as formatted string
		 * @see #getExpected()
		 */
		public String getExpectedAsString() {
			return Columns.getColumnNamesAsString(expected);
		}

		/**
		 * @return The value of {@link #getActual()} as formatted string
		 * @see #getActual()
		 */
		public String getActualAsString() {
			return Columns.getColumnNamesAsString(actual);
		}

		/**
		 * @return A pretty formatted message that can be used for user information
		 * @throws DataSetException
		 */
		public String getMessage() throws DataSetException 
		{
	        logger.debug("getMessage() - start");

			if(!this.hasDifference())
			{
				return NO_DIFFERENCE;
			}
			else
			{
	    		Column[] allExpectedCols = expectedMetaData.getColumns();
	    		Column[] allActualCols = actualMetaData.getColumns();
	    		String expectedTableName = expectedMetaData.getTableName();
	
	    		String message;
	    		if(allExpectedCols.length != allActualCols.length) 
	    		{
	    			message = "column count (table=" + expectedTableName + ", " +
	    					"expectedColCount=" + allExpectedCols.length + ", actualColCount=" + allActualCols.length + ")";
	    		}
	    		else 
	    		{
	    			message = "column mismatch (table=" + expectedTableName + ")";
	    		}
	    		return message;
			}
		}

//		/**
//		 * @return A pretty formatted message that shows up the difference
//		 */
//		private String toMessage()
//		{
//			StringBuffer sb = new StringBuffer();
//			sb.append("column-diffs (expected <-> actual): ");
//			if(this.hasDifference()) 
//			{
//				sb.append(getExpectedAsString());
//				sb.append(" <-> ");
//				sb.append(getActualAsString());
//			}
//			else
//			{
//				sb.append(NO_DIFFERENCE);
//			}
//			return sb.toString();
//		}
		
		public String toString()
    	{
    		final StringBuilder sb = new StringBuilder();
    		sb.append(getClass().getName()).append("[");
    		sb.append("expected=").append(Arrays.asList(expected).toString());
    		sb.append(", actual=").append(Arrays.asList(actual).toString());
    		sb.append("]");
    		return sb.toString();
    	}

    }


}
