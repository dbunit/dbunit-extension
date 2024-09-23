/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2005, DbUnit.org
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
package org.dbunit.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.dbunit.database.search.ForeignKeyRelationshipEdge;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.filter.AbstractTableFilter;
import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter a table given a map of the allowed rows based on primary key values.<br>
 * It uses a depth-first algorithm (although not recursive - it might be refactored
 * in the future) to define which rows are allowed, as well which rows are necessary
 * (and hence allowed) because of dependencies with the allowed rows.<br>
 * <strong>NOTE:</strong> multi-column primary keys are not supported at the moment.
 * TODO: test cases
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Sep 9, 2005
 */
public class PrimaryKeyFilter extends AbstractTableFilter {

    private final IDatabaseConnection connection;

    private final PkTableMap allowedPKsPerTable;
    private final PkTableMap allowedPKsInput;
    private final PkTableMap pksToScanPerTable;

    private final boolean reverseScan;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // cache the primary keys
    private final Map pkColumnPerTable = new HashMap();

    private final Map fkEdgesPerTable = new HashMap();
    private final Map fkReverseEdgesPerTable = new HashMap();

    // name of the tables, in reverse order of dependency
    private final List tableNames = new ArrayList();

    /**
     * Default constructor, it takes as input a map with desired rows in a final
     * dataset; the filter will ensure that the rows necessary by these initial rows
     * are also allowed (and so on...).
     * @param connection database connection
     * @param allowedPKs map of allowed rows, based on the primary keys (key is the name
     * of a table; value is a Set with allowed primary keys for that table)
     * @param reverseDependency flag indicating if the rows that depend on a row should
     * also be allowed by the filter
     */
    public PrimaryKeyFilter(IDatabaseConnection connection, PkTableMap allowedPKs, boolean reverseDependency) {
        this.connection = connection;    
        this.allowedPKsPerTable = new PkTableMap();    
        this.allowedPKsInput = allowedPKs;
        this.reverseScan = reverseDependency;

        // we need a deep copy here
        this.pksToScanPerTable = new PkTableMap(allowedPKs);
    }

    public void nodeAdded(Object node) {
        this.tableNames.add( node );
        if ( this.logger.isDebugEnabled() ) {
            this.logger.debug("nodeAdded: " + node );
        }
    }

    public void edgeAdded(ForeignKeyRelationshipEdge edge) {
        if ( this.logger.isDebugEnabled() ) {
            this.logger.debug("edgeAdded: " + edge );
        }
        // first add it to the "direct edges"
        String from = (String) edge.getFrom();
        Set edges = (Set) this.fkEdgesPerTable.get(from);
        if ( edges == null ) {
            edges = new HashSet();
            this.fkEdgesPerTable.put( from, edges );
        }
        if ( ! edges.contains(edge) ) {
            edges.add(edge);
        }

        // then add it to the "reverse edges"
        String to = (String) edge.getTo();
        edges = (Set) this.fkReverseEdgesPerTable.get(to);
        if ( edges == null ) {
            edges = new HashSet();
            this.fkReverseEdgesPerTable.put(to, edges);
        }
        if ( ! edges.contains(edge) ) {
            edges.add(edge);
        }

        // finally, update the PKs cache
        updatePkCache(to, edge);

    }

    /**
     * @see AbstractTableFilter
     */
    public boolean isValidName(String tableName) throws DataSetException {
        //    boolean isValid = this.allowedIds.containsKey(tableName);
        //    return isValid;
        return true;
    }

    public ITableIterator iterator(IDataSet dataSet, boolean reversed)
    throws DataSetException {
        if ( this.logger.isDebugEnabled() ) {
            this.logger.debug("Filter.iterator()" );
        }
        try {
            searchPKs(dataSet);
        } catch (SQLException e) {
            throw new DataSetException( e );
        }
        return new FilterIterator(reversed ? dataSet.reverseIterator() : dataSet
                .iterator());
    }

    private void searchPKs(IDataSet dataSet) throws DataSetException, SQLException {
        logger.debug("searchPKs(dataSet={}) - start", dataSet);

        int counter = 0;
        while ( !this.pksToScanPerTable.isEmpty() ) {
            counter ++;
            if ( this.logger.isDebugEnabled() ) {
                this.logger.debug( "RUN # " + counter );
            }

            for( int i=this.tableNames.size()-1; i>=0; i-- ) {
                String tableName = (String) this.tableNames.get(i);
                // TODO: support multi-column PKs
                String pkColumn = dataSet.getTable(tableName).getTableMetaData().getPrimaryKeys()[0].getColumnName();
                Set tmpSet = this.pksToScanPerTable.get( tableName );
                if ( tmpSet != null && ! tmpSet.isEmpty() ) {
                    Set pksToScan = new HashSet( tmpSet );
                    if ( this.logger.isDebugEnabled() ) {
                        this.logger.debug(  "before search: "+ tableName + "=>" + pksToScan );
                    }
                    scanPKs( tableName, pkColumn, pksToScan );
                    scanReversePKs( tableName, pksToScan );
                    allowPKs( tableName, pksToScan );
                    removePKsToScan( tableName, pksToScan );
                } // if
            } // for 
            removeScannedTables();
        } // while
        if ( this.logger.isDebugEnabled() ) {
            this.logger.debug( "Finished searchIds()" );
        }
    } 

    private void removeScannedTables() {
        logger.debug("removeScannedTables() - start");
        this.pksToScanPerTable.retainOnly(this.tableNames);
    }

    private void allowPKs(String table, Set newAllowedPKs) {
        logger.debug("allowPKs(table={}, newAllowedPKs={}) - start", table, newAllowedPKs);

        // then, add the new IDs, but checking if it should be allowed to add them
        Set forcedAllowedPKs = this.allowedPKsInput.get( table );
        if( forcedAllowedPKs == null || forcedAllowedPKs.isEmpty() ) {
            allowedPKsPerTable.addAll(table, newAllowedPKs );
        } else {
            for(Iterator iterator = newAllowedPKs.iterator(); iterator.hasNext(); ) {
                Object id = iterator.next();
                if( forcedAllowedPKs.contains(id) ) {
                    allowedPKsPerTable.add(table, id);
                } 
                else 
                {
                    logger.debug("Discarding id {} of table {} as it was not included in the input!", id, table);
                }
            }
        }
    }

    private void scanPKs( String table, String pkColumn, Set allowedIds ) throws SQLException {
        if (logger.isDebugEnabled())
        {
            logger.debug("scanPKs(table={}, pkColumn={}, allowedIds={}) - start",
                    new Object[]{ table, pkColumn, allowedIds });
        }

        Set fkEdges = (Set) this.fkEdgesPerTable.get( table );
        if ( fkEdges == null || fkEdges.isEmpty() ) {
            return;
        }
        // we need a temporary list as there is no warranty about the set order...
        List fkTables = new ArrayList( fkEdges.size() );
        final StringBuilder colsBuffer = new StringBuilder();
        for(Iterator iterator = fkEdges.iterator(); iterator.hasNext(); ) {
            ForeignKeyRelationshipEdge edge = (ForeignKeyRelationshipEdge) iterator.next();
            fkTables.add( edge.getTo() );
            colsBuffer.append( edge.getFKColumn() );
            if ( iterator.hasNext() ) {
                colsBuffer.append( ", " );
            }
        }
        // NOTE: make sure the query below is compatible standard SQL
        String sql = "SELECT " + colsBuffer + " FROM " + table + 
        " WHERE " + pkColumn + " = ? ";
        if ( this.logger.isDebugEnabled() ) {
            this.logger.debug( "SQL: " + sql );
        }

        scanPKs(table, sql, allowedIds, fkTables);
    }

    private void scanPKs(String table, String sql, Set allowedIds, List fkTables) throws SQLException
    {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = this.connection.getConnection().prepareStatement( sql );
            for(Iterator iterator = allowedIds.iterator(); iterator.hasNext(); ) {
                Object pk = iterator.next(); // id being scanned
                if( this.logger.isDebugEnabled() ) {
                    this.logger.debug("Executing sql for ? = " + pk );
                }
                pstmt.setObject( 1, pk );
                rs = pstmt.executeQuery();
                while( rs.next() ) {
                    for( int i=0; i<fkTables.size(); i++ ) {
                        String newTable = (String) fkTables.get(i);
                        Object fk = rs.getObject(i+1);
                        if( fk != null ) {
                            logger.debug("New ID: {}->{}", newTable, fk);
                            addPKToScan( newTable, fk );
                        } 
                        else {
                            logger.warn( "Found null FK for relationship {} =>{}", table, newTable );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("scanPKs()", e);
        }
        finally {
            // new in the finally block. has been in the catch only before
            SQLHelper.close( rs, pstmt );
        }
    }

    private void scanReversePKs(String table, Set pksToScan) throws SQLException {
        logger.debug("scanReversePKs(table={}, pksToScan={}) - start", table, pksToScan);

        if ( ! this.reverseScan ) {
            return; 
        }
        Set fkReverseEdges = (Set) this.fkReverseEdgesPerTable.get( table );
        if ( fkReverseEdges == null || fkReverseEdges.isEmpty() ) {
            return;
        }
        Iterator iterator = fkReverseEdges.iterator();
        while ( iterator.hasNext() ) {
            ForeignKeyRelationshipEdge edge = (ForeignKeyRelationshipEdge) iterator.next();
            addReverseEdge( edge, pksToScan );
        }
    }

    private void addReverseEdge(ForeignKeyRelationshipEdge edge, Set idsToScan) throws SQLException {
        logger.debug("addReverseEdge(edge={}, idsToScan=) - start", edge, idsToScan);

        String fkTable = (String) edge.getFrom();
        String fkColumn = edge.getFKColumn();
        String pkColumn = getPKColumn( fkTable );
        // NOTE: make sure the query below is compatible standard SQL
        String sql = "SELECT " + pkColumn + " FROM " + fkTable + " WHERE " + fkColumn + " = ? ";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            logger.debug("Preparing SQL query '{}'", sql);

            pstmt = this.connection.getConnection().prepareStatement(sql);
            for(Iterator iterator = idsToScan.iterator(); iterator.hasNext(); ) {
                Object pk = iterator.next();
                if ( this.logger.isDebugEnabled() ) {
                    this.logger.debug( "executing query '" + sql + "' for ? = " + pk );
                }
                pstmt.setObject( 1, pk );
                rs = pstmt.executeQuery();
                while( rs.next() ) {
                    Object fk = rs.getObject(1);
                    addPKToScan( fkTable, fk );
                }
            } 
        } finally {
            SQLHelper.close( rs, pstmt );
        }
    }

    private void updatePkCache(String table, ForeignKeyRelationshipEdge edge) {
        logger.debug("updatePkCache(to={}, edge={}) - start", table, edge);

        Object pkTo = this.pkColumnPerTable.get(table);
        if ( pkTo == null ) {
            String pkColumn = edge.getPKColumn();
            this.pkColumnPerTable.put( table, pkColumn );
        }
    }

    // TODO: support PKs with multiple values
    private String getPKColumn( String table ) throws SQLException {
        logger.debug("getPKColumn(table={}) - start", table);

        // Try to get the cached column
        String pkColumn = (String) this.pkColumnPerTable.get( table );
        if ( pkColumn == null ) {
            // If the column has not been cached until now retrieve it from the database connection
            pkColumn = SQLHelper.getPrimaryKeyColumn( this.connection.getConnection(), table );
            this.pkColumnPerTable.put( table, pkColumn );
        }
        return pkColumn;
    }


    private void removePKsToScan(String table, Set ids) {
        logger.debug("removePKsToScan(table={}, ids={}) - start", table, ids);

        Set pksToScan = this.pksToScanPerTable.get(table);
        if ( pksToScan != null ) {
            if ( pksToScan == ids ) {   
                throw new RuntimeException( "INTERNAL ERROR on removeIdsToScan() for table " + table );
            } else {
                pksToScan.removeAll( ids );
            }
        }    
    }

    private void addPKToScan(String table, Object pk) {
        logger.debug("addPKToScan(table={}, pk={}) - start", table, pk);

        // first, check if it wasn't added yet
        if(this.allowedPKsPerTable.contains(table, pk)) {
            if ( this.logger.isDebugEnabled() ) {
                this.logger.debug( "Discarding already scanned id=" + pk + " for table " + table );
            }
            return;
        }

        this.pksToScanPerTable.add(table, pk);
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("tableNames=").append(tableNames);
        sb.append(", allowedPKsInput=").append(allowedPKsInput);
        sb.append(", allowedPKsPerTable=").append(allowedPKsPerTable);
        sb.append(", fkEdgesPerTable=").append(fkEdgesPerTable);
        sb.append(", fkReverseEdgesPerTable=").append(fkReverseEdgesPerTable);
        sb.append(", pkColumnPerTable=").append(pkColumnPerTable);
        sb.append(", pksToScanPerTable=").append(pksToScanPerTable);
        sb.append(", reverseScan=").append(reverseScan);
        sb.append(", connection=").append(connection);
        return sb.toString();
    }


    private class FilterIterator implements ITableIterator {

        private final ITableIterator _iterator;

        public FilterIterator(ITableIterator iterator) {

            _iterator = iterator;
        }

        ////////////////////////////////////////////////////////////////////////////
        // ITableIterator interface

        public boolean next() throws DataSetException {
            if ( logger.isDebugEnabled() ) {
                logger.debug("Iterator.next()" );
            }      
            while (_iterator.next()) {
                if (accept(_iterator.getTableMetaData().getTableName())) {
                    return true;
                }
            }
            return false;
        }

        public ITableMetaData getTableMetaData() throws DataSetException {
            if ( logger.isDebugEnabled() ) {
                logger.debug("Iterator.getTableMetaData()" );
            }      
            return _iterator.getTableMetaData();
        }

        public ITable getTable() throws DataSetException {
            if ( logger.isDebugEnabled() ) {
                logger.debug("Iterator.getTable()" );
            }
            ITable table = _iterator.getTable();
            String tableName = table.getTableMetaData().getTableName();
            Set allowedPKs = allowedPKsPerTable.get( tableName );
            if ( allowedPKs != null ) {
                return new PrimaryKeyFilteredTableWrapper(table, allowedPKs);
            }
            return table;
        }
    }

    /**
     * Map that associates a table with a set of primary key objects.
     * 
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.3.0
     */
    public static class PkTableMap
    {
        private final LinkedHashMap pksPerTable;
        private final Logger logger = LoggerFactory.getLogger(PkTableMap.class);

        public PkTableMap()
        {
            this.pksPerTable = new LinkedHashMap();
        }

        /**
         * Copy constructor
         * @param allowedPKs
         */
        public PkTableMap(PkTableMap allowedPKs) {
            this.pksPerTable = new LinkedHashMap();
            Iterator iterator = allowedPKs.pksPerTable.entrySet().iterator();
            while ( iterator.hasNext() ) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String table = (String)entry.getKey();
                SortedSet pkObjectSet = (SortedSet) entry.getValue();
                SortedSet newSet = new TreeSet( pkObjectSet );
                this.pksPerTable.put( table, newSet );
            }
        }

        public int size() {
            return pksPerTable.size();
        }

        public boolean isEmpty() {
            return pksPerTable.isEmpty();
        }

        public boolean contains(String table, Object pkObject) {
            Set pksPerTable = this.get(table);
            return (pksPerTable != null && pksPerTable.contains(pkObject));
        }

        public void remove(String tableName) {
            this.pksPerTable.remove(tableName);
        }

        public void put(String table, SortedSet pkObjects) {
            this.pksPerTable.put(table, pkObjects);
        }

        public void add(String tableName, Object pkObject) {
            Set pksPerTable = getCreateIfNeeded(tableName);
            pksPerTable.add(pkObject);
        }

        public void addAll(String tableName, Set pkObjectsToAdd) {
            Set pksPerTable = this.getCreateIfNeeded(tableName);
            pksPerTable.addAll(pkObjectsToAdd);
        }

        public SortedSet get(String tableName) {
            return (SortedSet) this.pksPerTable.get(tableName);
        }

        private SortedSet getCreateIfNeeded(String tableName){
            SortedSet pksPerTable = this.get(tableName);
            // Lazily create the set if it did not exist yet
            if( pksPerTable == null ) {
                pksPerTable = new TreeSet();
                this.pksPerTable.put(tableName, pksPerTable);
            }
            return pksPerTable;
        }

        public String[] getTableNames() {
            return (String[]) this.pksPerTable.keySet().toArray(new String[0]);
        }

        public void retainOnly(List tableNames) {

            List tablesToRemove = new ArrayList();
            for(Iterator iterator = this.pksPerTable.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String table = (String) entry.getKey();
                SortedSet pksToScan = (SortedSet) entry.getValue();
                boolean removeIt = pksToScan.isEmpty();

                if ( ! tableNames.contains(table) ) {
                    if ( this.logger.isWarnEnabled() ) {
                        this.logger.warn("Discarding ids " + pksToScan + " of table " + table +
                        "as this table has not been passed as input" );
                    }
                    removeIt = true;
                }
                if ( removeIt ) {
                    tablesToRemove.add( table );
                }
            }

            for(Iterator iterator = tablesToRemove.iterator(); iterator.hasNext(); ) {
                this.remove( (String)iterator.next() );
            }
        }
        
        
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("pKsPerTable=").append(pksPerTable);
            return sb.toString();
        }

    }
}
