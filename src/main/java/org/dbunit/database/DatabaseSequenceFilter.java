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
package org.dbunit.database;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dbunit.database.search.ExportedKeysSearchCallback;
import org.dbunit.database.search.ImportedKeysSearchCallback;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.filter.SequenceTableFilter;
import org.dbunit.util.search.DepthFirstSearch;
import org.dbunit.util.search.ISearchCallback;
import org.dbunit.util.search.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter orders tables using dependency information provided by
 * {@link java.sql.DatabaseMetaData#getExportedKeys}. Note that this class
 * name is a bit misleading since it is not at all related to database
 * sequences. It just brings database tables in a specific order.
 *
 * @author Manuel Laflamme
 * @author Erik Price
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.5.1 (Mar 23, 2003)
 */
public class DatabaseSequenceFilter extends SequenceTableFilter
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSequenceFilter.class);
  

    /**
     * Create a DatabaseSequenceFilter that only exposes specified table names.
     */
    public DatabaseSequenceFilter(IDatabaseConnection connection,
            String[] tableNames) throws DataSetException, SQLException
    {
        super(sortTableNames(connection, tableNames));
    }

    /**
     * Create a DatabaseSequenceFilter that exposes all the database tables.
     */
    public DatabaseSequenceFilter(IDatabaseConnection connection)
            throws DataSetException, SQLException
    {
        this(connection, connection.createDataSet().getTableNames());
    }

    /**
     * Re-orders a string array of table names, placing dependent ("parent")
     * tables after their dependencies ("children").
     *
     * @param tableNames A string array of table names to be ordered.
     * @return The re-ordered array of table names.
     * @throws DataSetException
     * @throws SQLException If an exception is encountered in accessing the database.
     */
    static String[] sortTableNames(
        IDatabaseConnection connection,
        String[] tableNames)
        throws DataSetException, SQLException
            // not sure why this throws DataSetException ? - ENP
    {
        logger.debug("sortTableNames(connection={}, tableNames={}) - start", connection, tableNames);

        // Get dependencies for each table
        Map dependencies = new HashMap();
        // Per-invocation edge caches, shared across all tables below, so that a node
        // visited by more than one table's search (or by both its direct and transitive
        // searches) only ever triggers one getImportedKeys/getExportedKeys JDBC round trip.
        Map importedEdgesCache = new HashMap();
        Map exportedEdgesCache = new HashMap();
        try {
            for (int i = 0; i < tableNames.length; i++) {
                String tableName = tableNames[i];
                DependencyInfo info = getDependencyInfo(connection, tableName,
                        importedEdgesCache, exportedEdgesCache);
                dependencies.put(tableName, info);
            }
        } catch (SearchException e) {
            throw new DataSetException("Exception while searching the dependent tables.", e);
        }

        
        // Check whether the table dependency info contains cycles
        for (Iterator iterator = dependencies.values().iterator(); iterator.hasNext();) {
            DependencyInfo info = (DependencyInfo) iterator.next();
            info.checkCycles();
        }

        try {
            return sort(connection, tableNames, dependencies);
        } catch (SearchException e) {
            throw new DataSetException("Exception while searching the dependent tables.", e);
        }
    }


    /**
     * Topologically sorts {@code tableNames} via Kahn's algorithm, using each table's direct
     * dependency info: an edge runs from a table to each of its direct dependents (the tables
     * in its {@link DependencyInfo#getDirectDependentTablesSet()}), meaning the table must
     * precede those dependents in the result. Cycles are assumed already rejected by
     * {@link DependencyInfo#checkCycles()}, called earlier in {@link #sortTableNames}.
     * @param connection The database connection used to resolve the stored identifier case.
     * @param tableNames The table names to be ordered.
     * @param dependencies Each table name's {@link DependencyInfo}, keyed by table name.
     * @return The topologically sorted table names; when more than one valid order exists,
     * ties break to the original {@code tableNames} order.
     * @throws SearchException If the JDBC connection cannot be obtained.
     */
    private static String[] sort(IDatabaseConnection connection, String[] tableNames, Map dependencies)
    throws SearchException
    {
        logger.debug("sort(tableNames={}, dependencies={}) - start", tableNames, dependencies);

        int tableCount = tableNames.length;
        // Dependency-set entries (below) come back in the database's native identifier case
        // (e.g. lowercase on PostgreSQL), which can differ from the caller-supplied tableNames
        // case; index by that same normalized case so the edge lookups below actually match.
        String[] normalizedNames = normalizeToStoredCase(connection, tableNames);
        Map<String, Integer> nameToIndex = new HashMap<String, Integer>(tableCount);
        for (int i = 0; i < tableCount; i++)
        {
            nameToIndex.put(normalizedNames[i], i);
        }

        // In-degree = how many of this table's direct dependencies (prerequisites), among the
        // tables being sorted, have not yet been placed in the result.
        int[] inDegree = new int[tableCount];
        for (int i = 0; i < tableCount; i++)
        {
            DependencyInfo info = (DependencyInfo) dependencies.get(tableNames[i]);
            for (Iterator it = info.getDirectDependsOnTablesSet().iterator(); it.hasNext();)
            {
                if (nameToIndex.containsKey(it.next()))
                {
                    inDegree[i]++;
                }
            }
        }

        // Indices (not names) of tables with no remaining prerequisites. A TreeSet always
        // yields the smallest index first, so whenever several tables become ready at once,
        // the one appearing earliest in the original tableNames order is emitted first.
        TreeSet<Integer> ready = new TreeSet<Integer>();
        for (int i = 0; i < tableCount; i++)
        {
            if (inDegree[i] == 0)
            {
                ready.add(i);
            }
        }

        String[] sortedTableNames = new String[tableCount];
        int sortedCount = 0;
        while (!ready.isEmpty())
        {
            int index = ready.pollFirst();
            String tableName = tableNames[index];
            sortedTableNames[sortedCount++] = tableName;

            DependencyInfo info = (DependencyInfo) dependencies.get(tableName);
            for (Iterator it = info.getDirectDependentTablesSet().iterator(); it.hasNext();)
            {
                Integer dependentIndex = nameToIndex.get(it.next());
                if (dependentIndex != null)
                {
                    inDegree[dependentIndex]--;
                    if (inDegree[dependentIndex] == 0)
                    {
                        ready.add(dependentIndex);
                    }
                }
            }
        }

        return sortedTableNames;
    }

    /**
     * Creates the dependency information for the given table.
     * @param connection The database connection used to resolve foreign-key metadata.
     * @param tableName The table name for which to compute dependency information.
     * @param importedEdgesCache Per-{@code sortTableNames}-invocation cache of {@code ImportedKeysSearchCallback}
     * edges, shared across all tables being sorted; keyed by table name.
     * @param exportedEdgesCache Same as {@code importedEdgesCache}, for {@code ExportedKeysSearchCallback} edges.
     * @return The dependency information for the given table.
     * @throws SearchException If the JDBC connection cannot be obtained.
     */
    private static DependencyInfo getDependencyInfo(
            IDatabaseConnection connection, String tableName,
            Map importedEdgesCache, Map exportedEdgesCache)
    throws SearchException
    {
        logger.debug("getDependencyInfo(connection={}, tableName={}) - start", connection, tableName);

        // Equivalent to TablesDependencyHelper.getDependentTables/getDependsOnTables/
        // getDirectDependentTables/getDirectDependsOnTables, inlined here (rather than calling
        // those methods) so the same callback instance -- and therefore the same edge cache --
        // can be reused for both the direct and transitive searches below. Each does a depth
        // search for dependencies; the unlimited ones return the whole tree of dependent
        // objects, not only the direct FK-PK related tables.
        ISearchCallback importedCallback = new CachingSearchCallback(
                new ImportedKeysSearchCallback(connection), importedEdgesCache);
        ISearchCallback exportedCallback = new CachingSearchCallback(
                new ExportedKeysSearchCallback(connection), exportedEdgesCache);
        String[] normalizedRoot = normalizeToStoredCase(connection, new String[] {tableName});

        Set allDependsOnTablesSet = new DepthFirstSearch().search(normalizedRoot, importedCallback);
        Set allDependentTablesSet = new DepthFirstSearch().search(normalizedRoot, exportedCallback);
        // Remove the table itself which is automatically included by the search
        allDependentTablesSet.remove(normalizedRoot[0]);
        allDependsOnTablesSet.remove(normalizedRoot[0]);

        // Computed after the unlimited searches above: the root's edges (and, for the
        // exported-keys direction, its direct dependents' edges too) are already cached by
        // then, so these two calls are cache hits, not additional JDBC round trips.
        Set directDependsOnTablesSet = new DepthFirstSearch(1).search(normalizedRoot, importedCallback);
        Set directDependentTablesSet = new DepthFirstSearch(1).search(normalizedRoot, exportedCallback);
        directDependsOnTablesSet.remove(normalizedRoot[0]);
        directDependentTablesSet.remove(normalizedRoot[0]);

        DependencyInfo info = new DependencyInfo(tableName,
                directDependsOnTablesSet, directDependentTablesSet,
                allDependsOnTablesSet, allDependentTablesSet);
        return info;
    }

    /**
     * Lowercases the given table names when the database stores unquoted identifiers in
     * lowercase (e.g. PostgreSQL), so that {@link DepthFirstSearch}'s visited-node set stays
     * consistent with the lowercase FK-metadata names returned by the driver. Mirrors
     * {@code TablesDependencyHelper.normalizeToStoredCase}, duplicated here (rather than reused)
     * since it is private there and this class no longer calls through the helper's per-search
     * factory methods -- doing so would prevent the callback (and therefore its edge cache) from
     * being shared between the direct and transitive searches for the same table.
     * @param connection The database connection used to determine stored identifier case.
     * @param tableNames The table names to normalize.
     * @return The table names lowercased if needed, otherwise the original array.
     * @throws SearchException If the JDBC connection cannot be obtained.
     */
    private static String[] normalizeToStoredCase(IDatabaseConnection connection, String[] tableNames)
    throws SearchException
    {
        try
        {
            if (!connection.getConnection().getMetaData().storesLowerCaseIdentifiers())
            {
                return tableNames;
            }
            String[] normalized = new String[tableNames.length];
            for (int i = 0; i < tableNames.length; i++)
            {
                normalized[i] = tableNames[i].toLowerCase(Locale.ENGLISH);
            }
            return normalized;
        }
        catch (SQLException e)
        {
            throw new SearchException(e);
        }
    }

    /**
     * {@link ISearchCallback} decorator that memoizes {@link #getEdges(Object)} results in a
     * shared map, so repeated visits to the same node -- across the direct and transitive
     * searches for one table, and across different tables within one {@code sortTableNames}
     * invocation -- reuse the previously fetched JDBC metadata instead of re-querying it.
     */
    private static class CachingSearchCallback implements ISearchCallback
    {
        private final ISearchCallback delegate;
        private final Map edgesCache;

        CachingSearchCallback(ISearchCallback delegate, Map edgesCache)
        {
            this.delegate = delegate;
            this.edgesCache = edgesCache;
        }

        public SortedSet getEdges(Object fromNode) throws SearchException
        {
            if (edgesCache.containsKey(fromNode))
            {
                return (SortedSet) edgesCache.get(fromNode);
            }
            SortedSet edges = delegate.getEdges(fromNode);
            edgesCache.put(fromNode, edges);
            return edges;
        }

        public void nodeAdded(Object fromNode) throws SearchException
        {
            delegate.nodeAdded(fromNode);
        }

        public boolean searchNode(Object node) throws SearchException
        {
            return delegate.searchNode(node);
        }
    }


    
    /**
     * Container of dependency information for one single table.
     * 
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.4.0
     */
    static class DependencyInfo
    {
        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(DatabaseSequenceFilter.class);

        private String tableName;
        
        private Set allTableDependsOn;
        private Set allTableDependent;
        
        private Set directDependsOnTablesSet;
        private Set directDependentTablesSet;
        
        /**
         * @param tableName
         * @param allTableDependsOn Tables that are required as prerequisite so that this one can exist
         * @param allTableDependent Tables that need this one in order to be able to exist
         */
        public DependencyInfo(String tableName, 
                Set directDependsOnTablesSet, Set directDependentTablesSet,
                Set allTableDependsOn, Set allTableDependent) 
        {
            super();
            this.directDependsOnTablesSet = directDependsOnTablesSet;
            this.directDependentTablesSet = directDependentTablesSet;
            this.allTableDependsOn = allTableDependsOn;
            this.allTableDependent = allTableDependent;
            this.tableName = tableName;
        }

        public String getTableName() {
            return tableName;
        }

        public Set getAllTableDependsOn() {
            return allTableDependsOn;
        }

        public Set getAllTableDependent() {
            return allTableDependent;
        }
        
        public Set getDirectDependsOnTablesSet() {
            return directDependsOnTablesSet;
        }

        public Set getDirectDependentTablesSet() {
            return directDependentTablesSet;
        }

        /**
         * Checks this table's information for cycles by intersecting the two sets.
         * When the result set has at least one element we do have cycles.
         * @throws CyclicTablesDependencyException
         */
        public void checkCycles() throws CyclicTablesDependencyException 
        {
            logger.debug("checkCycles() - start");

            // Intersect the "tableDependsOn" and "otherTablesDependOn" to check for cycles
            Set intersect = new HashSet(this.allTableDependsOn);
            intersect.retainAll(this.allTableDependent);
            if(!intersect.isEmpty()){
                throw new CyclicTablesDependencyException(tableName, intersect);
            }
        }

        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("DependencyInfo[");
            sb.append("table=").append(tableName);
            sb.append(", directDependsOn=").append(directDependsOnTablesSet);
            sb.append(", directDependent=").append(directDependentTablesSet);
            sb.append(", allDependsOn=").append(allTableDependsOn);
            sb.append(", allDependent=").append(allTableDependent);
            sb.append("]");
            return sb.toString();
        }
        
    }
}
