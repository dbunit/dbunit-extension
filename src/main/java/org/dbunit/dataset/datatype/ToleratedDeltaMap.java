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
package org.dbunit.dataset.datatype;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container that manages a map of {@link ToleratedDelta} objects to be used for
 * numeric comparisons with an allowed deviation of two values
 * 
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
public class ToleratedDeltaMap
{
    /**
     * List of {@link ToleratedDelta} objects
     */
    private Map _toleratedDeltas;
    /**
     * The logger
     */
    private Logger logger = LoggerFactory.getLogger(ToleratedDeltaMap.class);

    /**
     * Lookup a tolerated delta object by tableName and ColumnName.
     * 
     * @param tableName
     * @param columnName
     * @return The object from the map or <code>null</code> if no such object
     *         was found
     */
    public ToleratedDelta findToleratedDelta(String tableName,
            String columnName)
    {
        Map toleratedDeltas = getToleratedDeltasNullSafe();
        String mapKey = ToleratedDeltaMap.buildMapKey(tableName, columnName);
        ToleratedDelta deltaObj = (ToleratedDelta) toleratedDeltas.get(mapKey);
        return deltaObj;
    }

    private final Map getToleratedDeltasNullSafe()
    {
        Map res = getToleratedDeltas();
        if (res == null)
        {
            return Collections.emptyMap();
        }
        return res;
    }

    public Map getToleratedDeltas()
    {
        return _toleratedDeltas;
    }

    /**
     * Adds a new object to the map of tolerated deltas
     * 
     * @param delta
     *            The object to be added to the map
     */
    public void addToleratedDelta(ToleratedDelta delta)
    {
        if (delta == null)
        {
            throw new NullPointerException(
                    "The parameter 'delta' must not be null");
        }

        if (this._toleratedDeltas == null)
        {
            this._toleratedDeltas = new HashMap();
        }
        String key = ToleratedDeltaMap.buildMapKey(delta);
        // Put the new object into the map
        ToleratedDelta removed =
                (ToleratedDelta) _toleratedDeltas.put(key, delta);
        // Give a hint to the user when an already existing object has been
        // overwritten/replaced
        if (removed != null)
        {
            logger.debug(
                    "Replaced old tolerated delta object from map with key {}. Old replaced object={}",
                    key, removed);
        }
    }

    /**
     * Utility method to create a map key from the input parameters
     * 
     * @param tableName
     * @param columnName
     * @return The key for the tolerated delta object map, consisting of the
     *         tableName and the columnName
     */
    static String buildMapKey(String tableName, String columnName)
    {
        return tableName + "." + columnName;
    }

    /**
     * Utility method to create a map key from the input parameters
     * 
     * @param delta
     * @return The key for the tolerated delta object map, consisting of the
     *         tableName and the columnName
     */
    static String buildMapKey(ToleratedDelta delta)
    {
        return buildMapKey(delta.getTableName(), delta.getColumnName());
    }

    /**
     * Simple bean that holds the tolerance for floating point comparisons for a
     * specific database column.
     */
    public static class ToleratedDelta
    {
        private String tableName;
        private String columnName;
        private Precision toleratedDelta;

        /**
         * @param tableName
         *            The name of the table
         * @param columnName
         *            The name of the column for which the tolerated delta
         *            should be applied
         * @param toleratedDelta
         *            The tolerated delta. For example 1E-5 means that the
         *            comparison must match the first 5 decimal digits. All
         *            subsequent decimals are ignored.
         */
        public ToleratedDelta(String tableName, String columnName,
                double toleratedDelta)
        {
            this(tableName, columnName, new Precision(
                    new BigDecimal(String.valueOf(toleratedDelta))));
        }

        /**
         * @param tableName
         *            The name of the table
         * @param columnName
         *            The name of the column for which the tolerated delta
         *            should be applied
         * @param toleratedDelta
         *            The tolerated delta. For example 1E-5 means that the
         *            comparison must match the first 5 decimal digits. All
         *            subsequent decimals are ignored.
         */
        public ToleratedDelta(String tableName, String columnName,
                BigDecimal toleratedDelta)
        {
            this(tableName, columnName, new Precision(toleratedDelta));
        }

        /**
         * @param tableName
         *            The name of the table
         * @param columnName
         *            The name of the column for which the tolerated delta
         *            should be applied
         * @param toleratedDelta
         *            The tolerated delta. For example 1E-5 means that the
         *            comparison must match the first 5 decimal digits. All
         *            subsequent decimals are ignored.
         * @param isPercentage
         *            Whether or not the given toleratedDelta value is a
         *            percentage. See {@link Precision} for more.
         */
        public ToleratedDelta(String tableName, String columnName,
                BigDecimal toleratedDelta, boolean isPercentage)
        {
            this(tableName, columnName,
                    new Precision(toleratedDelta, isPercentage));
        }

        /**
         * @param tableName
         *            The name of the table
         * @param columnName
         *            The name of the column for which the tolerated delta
         *            should be applied
         * @param toleratedDelta
         *            The tolerated delta. For example 1E-5 means that the
         *            comparison must match the first 5 decimal digits. All
         *            subsequent decimals are ignored.
         */
        public ToleratedDelta(String tableName, String columnName,
                Precision toleratedDelta)
        {
            super();
            this.tableName = tableName;
            this.columnName = columnName;
            this.toleratedDelta = toleratedDelta;
        }

        public String getTableName()
        {
            return tableName;
        }

        public String getColumnName()
        {
            return columnName;
        }

        public Precision getToleratedDelta()
        {
            return toleratedDelta;
        }

        /**
         * Checks whether or not the <code>tableName</code> and the
         * <code>columnName</code> match the ones of this object.
         * 
         * @param tableName
         * @param columnName
         * @return <code>true</code> if both given values match those of this
         *         object.
         */
        public boolean matches(String tableName, String columnName)
        {
            if (this.tableName.equals(tableName)
                    && this.columnName.equals(columnName))
            {
                return true;
            } else
            {
                return false;
            }
        }

        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("tableName=").append(tableName);
            sb.append(", columnName=").append(columnName);
            sb.append(", toleratedDelta=").append(toleratedDelta);
            return sb.toString();
        }
    }

    /**
     * Container for the tolerated delta of two values that are compared to each
     * other.
     * 
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.4.0
     */
    public static class Precision
    {
        private static final BigDecimal ZERO = new BigDecimal("0.0");

        private final boolean percentage;
        private final BigDecimal delta;

        /**
         * @param delta
         *            The allowed/tolerated difference
         */
        public Precision(BigDecimal delta)
        {
            this(delta, false);
        }

        /**
         * @param delta
         *            The allowed/tolerated difference
         * @param percentage
         *            Whether or not the given <code>delta</code> should be
         *            interpreted as percentage or not during the comparison
         */
        public Precision(BigDecimal delta, boolean percentage)
        {
            super();

            if (delta.compareTo(ZERO) < 0)
            {
                throw new IllegalArgumentException(
                        "The given delta '" + delta + "' must be >= 0");
            }

            this.delta = delta;
            this.percentage = percentage;
        }

        public boolean isPercentage()
        {
            return percentage;
        }

        public BigDecimal getDelta()
        {
            return delta;
        }

    }

}