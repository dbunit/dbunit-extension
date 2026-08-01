/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package org.dbunit.ext.mysql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MySqlMetadataHandler} for connections not restricted to a single schema (MySQL
 * "catalog"), e.g. a connection made as MySQL's "root" specifically to work across several
 * schemas at once with {@link org.dbunit.database.DatabaseConfig#FEATURE_QUALIFIED_TABLE_NAMES}
 * enabled.
 * <p>
 * {@link MySqlMetadataHandler}'s {@code getTables()}/{@code getColumns()}/{@code getPrimaryKeys()}
 * pass a {@code null} schema straight through as the JDBC {@code catalog} argument. Per the JDBC
 * specification {@code catalog=null} means "do not narrow the search by catalog", but MySQL
 * Connector/J instead treats it as "the connection's current catalog only" by default (its
 * {@code nullCatalogMeansCurrent} connection property), so tables in every other catalog are
 * silently invisible and a lookup for one of them fails with {@link org.dbunit.dataset.NoSuchTableException}.
 * <p>
 * This handler works around that driver behavior at the application level: whenever no single
 * schema is given, it enumerates the catalogs visible to the connection (skipping MySQL's own
 * {@code information_schema}/{@code mysql}/{@code performance_schema}/{@code sys} system
 * catalogs) and unions the per-catalog results instead of ever passing a {@code null} catalog to
 * the driver.
 * <p>
 * Configure it the same way as any other {@link org.dbunit.database.IMetadataHandler}:
 *
 * <pre>
 * config.setProperty(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, true);
 * config.setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER, new MultiSchemaMySqlMetadataHandler());
 * </pre>
 *
 * @since 3.4.1
 */
public class MultiSchemaMySqlMetadataHandler extends MySqlMetadataHandler
{
    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(MultiSchemaMySqlMetadataHandler.class);

    private static final Set<String> SYSTEM_CATALOGS = new HashSet<String>(Arrays.asList(
            "information_schema", "mysql", "performance_schema", "sys"));

    /**
     * Lazily-populated cache of {@link #listUserCatalogs}'s result, since one instance of this
     * handler is configured per connection (see the class Javadoc) and the visible catalogs are
     * not expected to change over that connection's lifetime.
     */
    private List<String> userCatalogs;

    @Override
    public ResultSet getTables(final DatabaseMetaData metaData, final String schemaName,
            final String[] tableType) throws SQLException
    {
        if (schemaName != null)
        {
            return super.getTables(metaData, schemaName, tableType);
        }

        final List<ResultSet> perCatalog = new ArrayList<ResultSet>();
        try
        {
            for (final String catalog : listUserCatalogs(metaData))
            {
                perCatalog.add(super.getTables(metaData, catalog, tableType));
            }
            return InMemoryMetadataResultSet.merge(perCatalog);
        }
        catch (final SQLException e)
        {
            closeAll(perCatalog);
            throw e;
        }
    }

    @Override
    public ResultSet getColumns(final DatabaseMetaData databaseMetaData, final String schemaName,
            final String tableName) throws SQLException
    {
        if (schemaName != null)
        {
            return super.getColumns(databaseMetaData, schemaName, tableName);
        }

        final List<ResultSet> perCatalog = new ArrayList<ResultSet>();
        try
        {
            for (final String catalog : listUserCatalogs(databaseMetaData))
            {
                perCatalog.add(super.getColumns(databaseMetaData, catalog, tableName));
            }
            return InMemoryMetadataResultSet.merge(perCatalog);
        }
        catch (final SQLException e)
        {
            closeAll(perCatalog);
            throw e;
        }
    }

    @Override
    public ResultSet getPrimaryKeys(final DatabaseMetaData metaData, final String schemaName,
            final String tableName) throws SQLException
    {
        if (schemaName != null)
        {
            return super.getPrimaryKeys(metaData, schemaName, tableName);
        }

        final List<ResultSet> perCatalog = new ArrayList<ResultSet>();
        try
        {
            for (final String catalog : listUserCatalogs(metaData))
            {
                perCatalog.add(super.getPrimaryKeys(metaData, catalog, tableName));
            }
            return InMemoryMetadataResultSet.merge(perCatalog);
        }
        catch (final SQLException e)
        {
            closeAll(perCatalog);
            throw e;
        }
    }

    @Override
    public boolean tableExists(final DatabaseMetaData metaData, final String schema,
            final String tableName) throws SQLException
    {
        if (schema != null)
        {
            return super.tableExists(metaData, schema, tableName);
        }

        for (final String catalog : listUserCatalogs(metaData))
        {
            if (super.tableExists(metaData, catalog, tableName))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists the catalogs visible to the connection, excluding MySQL's own system catalogs.
     * Cached after the first call; see {@link #userCatalogs}.
     *
     * @param metaData The database metadata to list catalogs from.
     * @return The visible, non-system catalog names.
     * @throws SQLException If the catalog list cannot be read.
     */
    private List<String> listUserCatalogs(final DatabaseMetaData metaData) throws SQLException
    {
        if (userCatalogs != null)
        {
            return userCatalogs;
        }

        final List<String> catalogs = new ArrayList<String>();
        final ResultSet catalogResultSet = metaData.getCatalogs();
        try
        {
            while (catalogResultSet.next())
            {
                final String catalog = catalogResultSet.getString(1);
                if (catalog != null && !SYSTEM_CATALOGS.contains(catalog.toLowerCase(Locale.ENGLISH)))
                {
                    catalogs.add(catalog);
                }
            }
        }
        finally
        {
            catalogResultSet.close();
        }

        if (logger.isDebugEnabled())
        {
            logger.debug("listUserCatalogs() - found {}", catalogs);
        }
        userCatalogs = catalogs;
        return catalogs;
    }

    /**
     * Closes every result set in the given list, null- and already-closed-safe.
     *
     * @param resultSets The result sets to close.
     * @throws SQLException If closing one of them fails.
     */
    private static void closeAll(final List<ResultSet> resultSets) throws SQLException
    {
        for (final ResultSet resultSet : resultSets)
        {
            SQLHelper.close(resultSet);
        }
    }

    /**
     * An in-memory {@link ResultSet}, backed by rows copied out of one or more source result sets
     * ahead of time. It supports only the handful of {@link ResultSet}/{@link ResultSetMetaData}
     * methods dbunit itself calls against an {@link org.dbunit.database.IMetadataHandler} result -
     * {@code next()}, {@code getString(int/String)}, {@code getInt(int/String)},
     * {@code getMetaData()}/{@code getColumnCount()}, {@code close()}, plus {@code equals()}/
     * {@code hashCode()}/{@code toString()} for safe use as a log argument or map key - since
     * implementing the rest of {@link ResultSet}'s ~150 methods would serve no caller. Any other
     * method throws {@link UnsupportedOperationException}.
     * <p>
     * A single instance answers both {@link ResultSet} calls and, since {@link #getMetaData()}
     * returns the proxy itself, the {@link ResultSetMetaData} calls made against its result.
     */
    private static final class InMemoryMetadataResultSet implements InvocationHandler
    {
        private final List<Object[]> rows;
        private final int columnCount;
        private final Map<String, Integer> columnIndexByLabel;
        private int cursor = -1;

        private InMemoryMetadataResultSet(final List<Object[]> rows, final int columnCount,
                final Map<String, Integer> columnIndexByLabel)
        {
            this.rows = rows;
            this.columnCount = columnCount;
            this.columnIndexByLabel = columnIndexByLabel;
        }

        /**
         * Copies every row of each given result set, closing each as it is consumed, and returns
         * a single merged {@link ResultSet} positioned before the first row.
         *
         * @param sources The result sets to merge, in the order their rows should appear.
         * @return The merged result set.
         * @throws SQLException If a source result set cannot be read.
         */
        static ResultSet merge(final List<ResultSet> sources) throws SQLException
        {
            final List<Object[]> rows = new ArrayList<Object[]>();
            final Map<String, Integer> columnIndexByLabel = new HashMap<String, Integer>();
            int columnCount = 0;
            boolean first = true;
            try
            {
                for (final ResultSet source : sources)
                {
                    if (first)
                    {
                        final ResultSetMetaData metaData = source.getMetaData();
                        columnCount = metaData.getColumnCount();
                        for (int i = 1; i <= columnCount; i++)
                        {
                            columnIndexByLabel.put(
                                    metaData.getColumnLabel(i).toUpperCase(Locale.ENGLISH), i);
                        }
                        first = false;
                    }
                    while (source.next())
                    {
                        final Object[] row = new Object[columnCount];
                        for (int i = 1; i <= columnCount; i++)
                        {
                            row[i - 1] = source.getObject(i);
                        }
                        rows.add(row);
                    }
                }
            }
            finally
            {
                closeAll(sources);
            }

            final InMemoryMetadataResultSet handler =
                    new InMemoryMetadataResultSet(rows, columnCount, columnIndexByLabel);
            return (ResultSet) Proxy.newProxyInstance(
                    InMemoryMetadataResultSet.class.getClassLoader(),
                    new Class<?>[] {ResultSet.class, ResultSetMetaData.class}, handler);
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args)
        {
            final String name = method.getName();
            if ("next".equals(name))
            {
                cursor++;
                return cursor < rows.size();
            }
            if ("getString".equals(name))
            {
                final Object value = currentValue(args[0]);
                return value == null ? null : String.valueOf(value);
            }
            if ("getInt".equals(name))
            {
                final Object value = currentValue(args[0]);
                return value == null ? 0 : toInt(value);
            }
            if ("getMetaData".equals(name))
            {
                return proxy;
            }
            if ("getColumnCount".equals(name))
            {
                return columnCount;
            }
            if ("close".equals(name))
            {
                return null;
            }
            if ("toString".equals(name))
            {
                return "InMemoryMetadataResultSet[rows=" + rows.size() + "]";
            }
            if ("hashCode".equals(name))
            {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name))
            {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(
                    "InMemoryMetadataResultSet does not support " + name + "()");
        }

        private Object currentValue(final Object columnArg)
        {
            if (cursor < 0 || cursor >= rows.size())
            {
                throw new IllegalStateException(
                        "ResultSet is not positioned on a valid row.");
            }
            return rows.get(cursor)[columnIndex(columnArg) - 1];
        }

        private int columnIndex(final Object columnArg)
        {
            if (columnArg instanceof Integer)
            {
                return (Integer) columnArg;
            }
            final String label = String.valueOf(columnArg).toUpperCase(Locale.ENGLISH);
            final Integer index = columnIndexByLabel.get(label);
            if (index == null)
            {
                throw new IllegalArgumentException("Unknown column '" + columnArg + "'.");
            }
            return index;
        }

        private static int toInt(final Object value)
        {
            if (value instanceof Number)
            {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        }
    }
}
