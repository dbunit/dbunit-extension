/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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
package org.dbunit.database.search;

import java.sql.SQLException;
import java.util.Set;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.PrimaryKeyFilter.PkTableMap;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.util.CollectionsHelper;
import org.dbunit.util.search.DepthFirstSearch;
import org.dbunit.util.search.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Helper for the graph-search based classes used to calculate dependency
 * among tables.  
 * 
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Aug 26, 2005
 */
public class TablesDependencyHelper {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(TablesDependencyHelper.class);

    // this is a "static" class
    private TablesDependencyHelper() {
    }

    /**
     * Get the name of all tables that depend on the root tables (i.e, all tables that have FKs
     * pointing to the PK of the root table).
     * @param connection database connection
     * @param rootTable root table described above
     * @return name of all tables that depend on the root table (including the root table), 
     * in the right order for insertions
     * @throws SearchException if an exception occurred while calculating the order
     */
    public static String[] getDependentTables( IDatabaseConnection connection, String rootTable ) 
    throws SearchException 
    {
        logger.debug("getDependentTables(connection={}, rootTable={}) - start", connection, rootTable);
        return getDependentTables( connection, new String[] { rootTable } );    
    }

    /**
     * Get the name of all tables that depend on the root tables (i.e, all tables that have FKs
     * pointing to the PK of one of the root tables).
     * @param connection database connection
     * @param rootTables array of root tables described above
     * @return name of all tables that depend on the root tables (including the root tables),
     * in the right order for insertions
     * @throws SearchException if an exception occurred while calculating the order
     */
    public static String[] getDependentTables( IDatabaseConnection connection, String[] rootTables )
    throws SearchException
    {
        logger.debug("getDependentTables(connection={}, rootTables={}) - start", connection, rootTables);

        ImportedKeysSearchCallback callback = new ImportedKeysSearchCallback(connection);
        DepthFirstSearch search = new DepthFirstSearch();
        Set tables = search.search( normalizeToStoredCase(connection, rootTables), callback );
        return CollectionsHelper.setToStrings( tables );
    }

    /**
     * Get the name of all tables that the given rootTable depends on (i.e, all tables whose PK is a FK for the root table). 
     * @param connection database connection
     * @param rootTable root table described above
     * @return name of all tables that the rootTable depends on (including the rootTable itself), 
     * in the right order for insertions
     * @throws SearchException if an exception occurred while calculating the order
     * @since 2.4
     */
    public static String[] getDependsOnTables( IDatabaseConnection connection, String rootTable ) 
    throws SearchException 
    {
        logger.debug("getDependsOnTables(connection={}, rootTable={}) - start", connection, rootTable);

        ExportedKeysSearchCallback callback = new ExportedKeysSearchCallback(connection);
        DepthFirstSearch search = new DepthFirstSearch();
        Set tables = search.search( normalizeToStoredCase(connection, new String[]{rootTable}), callback );
        return CollectionsHelper.setToStrings( tables );
    }

    /**
     * Get the name of all tables that depend on a root table ( i.e, all tables whose PK
     * is a FK for the root table) and also the tables the root table depends on 
     * (i.e., all tables which have a FK for the root table's PK). 
     * @param connection database connection
     * @param rootTable root table described above
     * @return name of all tables that depend on the root table (including the root table), 
     * in the right order for insertions
     * @throws SearchException if an exception occurred while calculating the order
     */
    public static String[] getAllDependentTables( IDatabaseConnection connection, String rootTable ) 
    throws SearchException 
    {
        logger.debug("getAllDependentTables(connection={}, rootTable={}) - start", connection, rootTable);
        return getAllDependentTables( connection, new String[] { rootTable } );
    }

    /**
     * Get the name of all tables that depend on the root tables ( i.e, all tables whose PK
     * is a FK for any of the root tables) and also the tables the root tables depends on 
     * (i.e., all tables which have a FK for any of the root table's PK). 
     * @param connection database connection
     * @param rootTables root tables described above
     * @return name of all tables that depend on the root tables (including the root tables), 
     * in the right order for insertions
     * @throws SearchException if an exception occurred while calculating the order
     */
    public static String[] getAllDependentTables(IDatabaseConnection connection, String[] rootTables)
    throws SearchException
    {
        logger.debug("getAllDependentTables(connection={}, rootTables={}) - start",connection, rootTables);

        ImportedAndExportedKeysSearchCallback callback = new ImportedAndExportedKeysSearchCallback(connection);
        DepthFirstSearch search = new DepthFirstSearch();
        Set tables = search.search(normalizeToStoredCase(connection, rootTables), callback);
        return CollectionsHelper.setToStrings(tables);
    }

    // TODO: javadoc (and unit tests) from down here...

    public static IDataSet getDataset(IDatabaseConnection connection,String rootTable, Set allowedIds) 
    throws SearchException, SQLException, DataSetException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("getDataset(connection={}, rootTable={}, allowedIds={}) - start", 
                    new Object[] { connection, rootTable, allowedIds });
        }

        PkTableMap map = new PkTableMap();
        map.addAll(rootTable, allowedIds);
        return getDataset(connection, map);
    }

    public static IDataSet getDataset( IDatabaseConnection connection, PkTableMap rootTables ) 
    throws SearchException, SQLException, DataSetException 
    {
        logger.debug("getDataset(connection={}, rootTables={}) - start", connection, rootTables);

        ImportedKeysSearchCallbackFilteredByPKs callback = new ImportedKeysSearchCallbackFilteredByPKs(connection, rootTables);
        ITableFilter filter = callback.getFilter();
        DepthFirstSearch search = new DepthFirstSearch();
        String[] tableNames = normalizeToStoredCase(connection, rootTables.getTableNames());
        Set tmpTables = search.search( tableNames, callback );
        String[] dependentTables  = CollectionsHelper.setToStrings( tmpTables );
        IDataSet tmpDataset = connection.createDataSet( dependentTables );
        FilteredDataSet dataset = new FilteredDataSet(filter, tmpDataset);
        return dataset;
    }

    public static IDataSet getAllDataset( IDatabaseConnection connection, String rootTable, Set allowedPKs ) 
    throws SearchException, SQLException, DataSetException 
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("getAllDataset(connection={}, rootTable={}, allowedPKs={}) - start", 
                    new Object[]{ connection, rootTable, allowedPKs });
        }

        PkTableMap map = new PkTableMap();
        map.addAll( rootTable, allowedPKs );
        return getAllDataset( connection, map );
    }

    public static IDataSet getAllDataset( IDatabaseConnection connection, PkTableMap rootTables ) 
    throws SearchException, SQLException, DataSetException 
    {
        logger.debug("getAllDataset(connection={}, rootTables={}) - start", connection, rootTables);

        ImportedAndExportedKeysSearchCallbackFilteredByPKs callback = new ImportedAndExportedKeysSearchCallbackFilteredByPKs(connection, rootTables);
        ITableFilter filter = callback.getFilter();
        DepthFirstSearch search = new DepthFirstSearch();
        String[] tableNames = normalizeToStoredCase(connection, rootTables.getTableNames());
        Set tmpTables = search.search( tableNames, callback );
        String[] dependentTables  = CollectionsHelper.setToStrings( tmpTables );
        IDataSet tmpDataset = connection.createDataSet( dependentTables );
        FilteredDataSet dataset = new FilteredDataSet(filter, tmpDataset);
        return dataset;
    }

    /**
     * Returns a set of tables on which the given table directly depends on.
     * @param connection The connection to be used for the database lookup.
     * @param tableName
     * @return a set of tables on which the given table directly depends on.
     * @throws SearchException
     * @since 2.4
     */
    public static Set getDirectDependsOnTables(IDatabaseConnection connection,
            String tableName) throws SearchException 
    {
        logger.debug("getDirectDependsOnTables(connection={}, tableName={}) - start", 
                    connection, tableName);
        
        ExportedKeysSearchCallback callback = new ExportedKeysSearchCallback(connection);
        // Do a depthFirstSearch with a recursion depth of 1
        DepthFirstSearch search = new DepthFirstSearch(1);
        Set tables = search.search( normalizeToStoredCase(connection, new String[]{tableName}), callback );
        return tables;
    }

    /**
     * Returns a set of tables which directly depend on the given table.
     * @param connection The connection to be used for the database lookup.
     * @param tableName
     * @return a set of tables on which the given table directly depends on.
     * @throws SearchException
     * @since 2.4
     */
    public static Set getDirectDependentTables(IDatabaseConnection connection,
            String tableName) throws SearchException 
    {
        logger.debug("getDirectDependentTables(connection={}, tableName={}) - start", 
                    connection, tableName);

        ImportedKeysSearchCallback callback = new ImportedKeysSearchCallback(connection);
        // Do a depthFirstSearch with a recursion depth of 1
        DepthFirstSearch search = new DepthFirstSearch(1);
        Set tables = search.search( normalizeToStoredCase(connection, new String[]{tableName}), callback );
        return tables;
    }

    /**
     * Lowercases the given table names when the database stores unquoted
     * identifiers in lowercase (e.g. PostgreSQL), so that
     * {@link DepthFirstSearch}'s visited-node set stays consistent with the
     * lowercase FK-metadata names returned by the driver. For databases that
     * store uppercase or mixed-case identifiers the names are returned as-is,
     * because uppercase normalization would corrupt quoted mixed-case
     * identifiers that are already in their stored form.
     *
     * @param connection the database connection used to determine stored identifier case.
     * @param tableNames the table names to normalize.
     * @return the table names lowercased if needed, otherwise the original array.
     * @throws SearchException if the JDBC connection cannot be obtained.
     */
    private static String[] normalizeToStoredCase(final IDatabaseConnection connection,
            final String[] tableNames) throws SearchException
    {
        try
        {
            if (!connection.getConnection().getMetaData().storesLowerCaseIdentifiers())
            {
                return tableNames;
            }
            final String[] normalized = new String[tableNames.length];
            for (int i = 0; i < tableNames.length; i++)
            {
                normalized[i] = tableNames[i].toLowerCase();
            }
            return normalized;
        }
        catch (final SQLException e)
        {
            throw new SearchException(e);
        }
    }

}
