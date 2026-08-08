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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
 * <p>A foreign-key dependency cycle among the ordered tables is rejected with
 * {@link CyclicTablesDependencyException} by default. Enable
 * {@link DatabaseConfig#FEATURE_SKIP_CYCLE_CHECK} to opt out of that check for schemas whose
 * cyclic references are handled another way (e.g. nullable FK columns populated in a later
 * operation, or database-side deferred constraint checking); tables outside the cycle are
 * still correctly ordered relative to it, and only the relative order of the cyclic tables
 * themselves is left as-supplied (see {@link #sort}).
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
     *
     * @param connection the database connection used to resolve table dependencies.
     * @param tableNames the table names to expose, re-ordered to respect FK dependencies.
     * @throws DataSetException if a table dependency cycle is detected and
     * {@link DatabaseConfig#FEATURE_SKIP_CYCLE_CHECK} is not enabled on {@code connection}'s
     * {@link DatabaseConfig}.
     * @throws SQLException if an exception is encountered in accessing the database.
     */
    public DatabaseSequenceFilter(IDatabaseConnection connection,
            String[] tableNames) throws DataSetException, SQLException
    {
        super(sortTableNames(connection, tableNames));
    }

    /**
     * Create a DatabaseSequenceFilter that exposes all the database tables.
     *
     * @param connection the database connection used to resolve table dependencies.
     * @throws DataSetException if a table dependency cycle is detected and
     * {@link DatabaseConfig#FEATURE_SKIP_CYCLE_CHECK} is not enabled on {@code connection}'s
     * {@link DatabaseConfig}.
     * @throws SQLException if an exception is encountered in accessing the database.
     */
    public DatabaseSequenceFilter(IDatabaseConnection connection)
            throws DataSetException, SQLException
    {
        this(connection, connection.createDataSet().getTableNames());
    }

    /**
     * Re-orders a string array of table names, placing dependent ("parent")
     * tables after their dependencies ("children"). Unless
     * {@link DatabaseConfig#FEATURE_SKIP_CYCLE_CHECK} is enabled on {@code connection}'s
     * {@link DatabaseConfig}, a foreign-key dependency cycle among {@code tableNames} is
     * rejected. When that feature is enabled, a cyclic group of tables is instead treated as
     * one unit for ordering purposes (see {@link #sort}): tables outside the cycle still
     * respect their real foreign-key dependencies on it, but the relative order of the tables
     * making up the cycle itself falls back to their original {@code tableNames} order.
     *
     * @param tableNames A string array of table names to be ordered.
     * @return The re-ordered array of table names.
     * @throws DataSetException if a table dependency cycle is detected and
     * {@link DatabaseConfig#FEATURE_SKIP_CYCLE_CHECK} is not enabled.
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
        String[] normalizedNames;
        try {
            for (int i = 0; i < tableNames.length; i++) {
                String tableName = tableNames[i];
                DependencyInfo info = getDependencyInfo(connection, tableName,
                        importedEdgesCache, exportedEdgesCache);
                dependencies.put(tableName, info);
            }
            // Dependency-set entries come back in the database's native identifier case (e.g.
            // lowercase on PostgreSQL), which can differ from the caller-supplied tableNames
            // case; normalize here so both sort()/componentsOf()'s edge lookups and the
            // cycle-dedup below key on the same case as the intersect sets they compare against.
            normalizedNames = normalizeToStoredCase(connection, tableNames);
        } catch (SearchException e) {
            throw new DataSetException("Exception while searching the dependent tables.", e);
        }

        // Check whether the table dependency info contains cycles, unless the caller opted out
        // via FEATURE_SKIP_CYCLE_CHECK. When skipping, log at most one warning per distinct
        // cycle rather than once per table participating in it.
        boolean skipCycleCheck =
                connection.getConfig().getFeature(DatabaseConfig.FEATURE_SKIP_CYCLE_CHECK);
        Set<String> reportedCyclicTables = new HashSet<String>();
        for (int i = 0; i < tableNames.length; i++) {
            DependencyInfo info = (DependencyInfo) dependencies.get(tableNames[i]);
            try
            {
                info.checkCycles();
            }
            catch (CyclicTablesDependencyException e)
            {
                if (!skipCycleCheck)
                {
                    throw e;
                }
                if (reportedCyclicTables.add(normalizedNames[i]))
                {
                    reportedCyclicTables.addAll(info.getCyclicDependencies());
                    logger.warn("Table dependency cycle detected but ignored because "
                            + "FEATURE_SKIP_CYCLE_CHECK is enabled: {}", e.getMessage());
                }
            }
        }

        return sort(tableNames, normalizedNames, dependencies);
    }


    /**
     * Topologically sorts {@code tableNames}. Tables are first grouped into strongly connected
     * components (SCCs) via {@link #componentsOf}: two tables share a component exactly when
     * {@link DependencyInfo#checkCycles()} would consider them part of the same cycle. With
     * {@link DatabaseConfig#FEATURE_SKIP_CYCLE_CHECK} off, {@link #sortTableNames} has already
     * rejected any real cycle via {@code checkCycles()}, so every component here is a
     * singleton and this reduces to an ordinary per-table topological sort via Kahn's
     * algorithm. When that feature lets a cycle through instead, the condensed graph of
     * components -- always acyclic, since collapsing each cycle into one node cannot itself
     * form a cycle -- is topologically sorted the same way, then each component is expanded
     * back into its member tables in their original {@code tableNames} order. A table that
     * merely depends on a cyclic table, without itself being part of the cycle, is therefore
     * still correctly ordered after the whole component it depends on; only the relative order
     * of tables within the same cyclic component is unresolved and falls back to
     * {@code tableNames} order.
     * @param tableNames The table names to be ordered.
     * @param normalizedNames {@code tableNames} normalized to the database's stored identifier
     * case, in the same order (see {@link #normalizeToStoredCase}).
     * @param dependencies Each table name's {@link DependencyInfo}, keyed by table name.
     * @return The topologically sorted table names; when more than one valid order exists,
     * ties break to the original {@code tableNames} order.
     * @throws IllegalStateException if the condensed component graph turns out not to be
     * acyclic, which would otherwise be an internal bug in {@link #componentsOf}.
     */
    private static String[] sort(String[] tableNames, String[] normalizedNames, Map dependencies)
    {
        logger.debug("sort(tableNames={}, dependencies={}) - start", tableNames, dependencies);

        int tableCount = tableNames.length;
        Map<String, Integer> nameToIndex = new HashMap<String, Integer>(tableCount);
        for (int i = 0; i < tableCount; i++)
        {
            nameToIndex.put(normalizedNames[i], i);
        }

        int[] componentOf = componentsOf(tableNames, normalizedNames, dependencies);
        int componentCount = 0;
        for (int i = 0; i < tableCount; i++)
        {
            componentCount = Math.max(componentCount, componentOf[i] + 1);
        }

        // Component-level direct-dependency edges, deduplicated (via Set) so that multiple
        // cross-component table pairs don't inflate a component's in-degree.
        List<Set<Integer>> componentDependsOn = new ArrayList<Set<Integer>>(componentCount);
        List<Set<Integer>> componentDependents = new ArrayList<Set<Integer>>(componentCount);
        for (int c = 0; c < componentCount; c++)
        {
            componentDependsOn.add(new HashSet<Integer>());
            componentDependents.add(new HashSet<Integer>());
        }
        for (int i = 0; i < tableCount; i++)
        {
            DependencyInfo info = (DependencyInfo) dependencies.get(tableNames[i]);
            for (Iterator it = info.getDirectDependsOnTablesSet().iterator(); it.hasNext();)
            {
                Integer dependencyIndex = nameToIndex.get(it.next());
                if (dependencyIndex != null && componentOf[dependencyIndex] != componentOf[i])
                {
                    componentDependsOn.get(componentOf[i]).add(componentOf[dependencyIndex]);
                }
            }
        }
        for (int c = 0; c < componentCount; c++)
        {
            for (Integer dependency : componentDependsOn.get(c))
            {
                componentDependents.get(dependency).add(c);
            }
        }

        // In-degree = how many other components this component directly depends on. A TreeSet
        // always yields the smallest component id first; component ids are assigned in
        // tableNames order (see componentsOf()), so whenever several components become ready at
        // once, the one containing the earliest original table is emitted first.
        int[] componentInDegree = new int[componentCount];
        TreeSet<Integer> readyComponents = new TreeSet<Integer>();
        for (int c = 0; c < componentCount; c++)
        {
            componentInDegree[c] = componentDependsOn.get(c).size();
            if (componentInDegree[c] == 0)
            {
                readyComponents.add(c);
            }
        }

        int[] sortedComponents = new int[componentCount];
        int sortedComponentCount = 0;
        while (!readyComponents.isEmpty())
        {
            int component = readyComponents.pollFirst();
            sortedComponents[sortedComponentCount++] = component;

            for (Integer dependentComponent : componentDependents.get(component))
            {
                componentInDegree[dependentComponent]--;
                if (componentInDegree[dependentComponent] == 0)
                {
                    readyComponents.add(dependentComponent);
                }
            }
        }

        // The condensed component graph is always acyclic by construction (see class Javadoc),
        // so Kahn's algorithm above must schedule every component; a shortfall here means that
        // guarantee was violated (e.g. an incomplete DepthFirstSearch closure), and continuing
        // would silently return sortedTableNames with trailing null entries instead.
        if (sortedComponentCount != componentCount)
        {
            throw new IllegalStateException("Condensed table-dependency graph is not acyclic: "
                    + "topologically sorted " + sortedComponentCount + " of " + componentCount
                    + " components.");
        }

        // Expand each component back into its member tables, in their original tableNames
        // order, so a multi-table cyclic component's own internal order is the input order.
        String[] sortedTableNames = new String[tableCount];
        int sortedCount = 0;
        for (int s = 0; s < sortedComponentCount; s++)
        {
            int component = sortedComponents[s];
            for (int i = 0; i < tableCount; i++)
            {
                if (componentOf[i] == component)
                {
                    sortedTableNames[sortedCount++] = tableNames[i];
                }
            }
        }

        return sortedTableNames;
    }

    /**
     * Assigns each table in {@code tableNames} to a strongly connected component, numbered in
     * the order each component is first encountered while scanning {@code tableNames}. Two
     * tables share a component exactly when {@link DependencyInfo#checkCycles()} would consider
     * them part of the same cycle: each can transitively reach the other via direct foreign-key
     * edges. A table outside any cycle -- the only possibility when {@link #sortTableNames} has
     * not skipped its {@code checkCycles()} call -- forms its own singleton component.
     * @param tableNames The table names being ordered.
     * @param normalizedNames {@code tableNames} normalized to the database's stored identifier
     * case, in the same order.
     * @param dependencies Each table name's {@link DependencyInfo}, keyed by table name.
     * @return Each table's component id, parallel to {@code tableNames}.
     */
    private static int[] componentsOf(String[] tableNames, String[] normalizedNames, Map dependencies)
    {
        int tableCount = tableNames.length;
        int[] componentOf = new int[tableCount];
        for (int i = 0; i < tableCount; i++)
        {
            componentOf[i] = -1;
        }

        int nextComponent = 0;
        for (int i = 0; i < tableCount; i++)
        {
            if (componentOf[i] != -1)
            {
                continue;
            }

            DependencyInfo info = (DependencyInfo) dependencies.get(tableNames[i]);
            Set mutuallyReachable = info.getCyclicDependencies();

            componentOf[i] = nextComponent;
            for (int j = i + 1; j < tableCount; j++)
            {
                if (componentOf[j] == -1 && mutuallyReachable.contains(normalizedNames[j]))
                {
                    componentOf[j] = nextComponent;
                }
            }
            nextComponent++;
        }
        return componentOf;
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
        // getDirectDependsOnTables, inlined here (rather than calling those methods) so the
        // same callback instance -- and therefore the same edge cache -- can be reused for
        // both the direct and transitive searches below. Each does a depth search for
        // dependencies; the unlimited ones return the whole tree of dependent objects, not
        // only the direct FK-PK related tables.
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

        // Computed after the unlimited search above: the root's edges are already cached by
        // then, so this call is a cache hit, not an additional JDBC round trip.
        Set directDependsOnTablesSet = new DepthFirstSearch(1).search(normalizedRoot, importedCallback);
        directDependsOnTablesSet.remove(normalizedRoot[0]);

        DependencyInfo info = new DependencyInfo(tableName,
                directDependsOnTablesSet, allDependsOnTablesSet, allDependentTablesSet);
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

        /**
         * Creates the dependency information for one table.
         *
         * @param tableName The name of the table this information describes.
         * @param directDependsOnTablesSet The tables this one directly references through a
         * foreign key.
         * @param allTableDependsOn Tables that are required as prerequisite so that this one can exist.
         * @param allTableDependent Tables that need this one in order to be able to exist.
         */
        public DependencyInfo(String tableName,
                Set directDependsOnTablesSet,
                Set allTableDependsOn, Set allTableDependent)
        {
            super();
            this.directDependsOnTablesSet = directDependsOnTablesSet;
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

        /**
         * Returns the tables this one directly references through a foreign key.
         *
         * @return The direct prerequisite tables.
         */
        public Set getDirectDependsOnTablesSet() {
            return directDependsOnTablesSet;
        }

        /**
         * Computes the tables sharing a foreign-key dependency cycle with this one, by
         * intersecting the tables this one depends on with the tables that depend on it.
         * @return The other tables in this table's dependency cycle, or an empty set if this
         * table is not part of any cycle.
         */
        public Set getCyclicDependencies()
        {
            Set intersect = new HashSet(this.allTableDependsOn);
            intersect.retainAll(this.allTableDependent);
            return intersect;
        }

        /**
         * Checks this table's information for cycles by intersecting the two sets.
         * When the result set has at least one element we do have cycles.
         * @throws CyclicTablesDependencyException
         */
        public void checkCycles() throws CyclicTablesDependencyException
        {
            logger.debug("checkCycles() - start");

            Set intersect = getCyclicDependencies();
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
            sb.append(", allDependsOn=").append(allTableDependsOn);
            sb.append(", allDependent=").append(allTableDependent);
            sb.append("]");
            return sb.toString();
        }

    }
}
