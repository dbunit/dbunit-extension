/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2004-2008, DbUnit.org
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
package org.dbunit.util;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to parse a fully qualified table name into its components <i>schema</i> and <i>table</i>.
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
public class QualifiedTableName
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(QualifiedTableName.class);

	private String schema;
	private String table;
	private String escapePattern;
	
	/**
	 * Creates an object parsing the given tableName.
	 * @param tableName The table name, either qualified or unqualified. If it is qualified (like "MYSCHEMA.MYTABLE")
	 * this schema name has precedence before the given <code>defaultSchema</code> parameter.
	 * @param defaultSchema The schema that is used when the given tableName is not fully qualified
	 * (i.e. it is not like "MYSCHEMA.MYTABLE"). Can be null
	 */
	public QualifiedTableName(String tableName, String defaultSchema)
	{
		this(tableName, defaultSchema, null);
	}
	
	/**
	 * Creates an object parsing the given tableName.
	 * @param tableName The table name, either qualified or unqualified. If it is qualified (like "MYSCHEMA.MYTABLE")
	 * this schema name has precedence before the given <code>defaultSchema</code> parameter.
	 * @param defaultSchema The schema that is used when the given tableName is not fully qualified
	 * (i.e. it is not like "MYSCHEMA.MYTABLE"). Can be null
     * @param escapePattern The escape pattern to be applied on the prefix and the name. Can be null.
	 */
	public QualifiedTableName(String tableName, String defaultSchema, String escapePattern)
	{
		if(tableName==null){
			throw new NullPointerException("The parameter 'tableName' must not be null");
		}
    	parseFullTableName(tableName, defaultSchema);
    	this.escapePattern = escapePattern;
	}

	/**
	 * Parses the given full table name into a schema name and a table name if available. If
	 * no schema is set the value of the {@link #getSchema()} is null.
	 * Sets the corresponding members of this class if found.
	 * @param fullTableName potentially fully qualified table name
	 * @param defaultSchema The schema that is used when the given tableName is not fully qualified
	 * (i.e. it is not like "MYSCHEMA.MYTABLE"). Can be null
	 */
	private void parseFullTableName(String fullTableName, String defaultSchema)
	{
		if(fullTableName==null){
			throw new NullPointerException("The parameter 'fullTableName' must not be null");
		}
        // check if a schema is in front
		int firstDotIndex = fullTableName.indexOf(".");
        if (firstDotIndex != -1) {
            // set schema
        	this.schema = fullTableName.substring(0, firstDotIndex);
            // set table name without schema
        	this.table = fullTableName.substring(firstDotIndex + 1);
        }
        else 
        {
        	// No schema name found in table
        	this.table = fullTableName;
        	// If the schema has not been found in the given table name 
        	// (that means there is no "MYSCHEMA.MYTABLE" but only a "MYTABLE")
        	// then set the schema to the given default schema
    		this.schema = defaultSchema;
        }
	}

	/**
	 * @return The schema name which can be null if no schema has been given in the constructor
	 */
	public String getSchema() {
		return schema;
	}

	/**
	 * @return The name of the plain, unqualified table
	 */
	public String getTable() {
		return table;
	}

	/**
	 * @return The qualified table name with the prepended schema if a schema is available
	 */
	public String getQualifiedName() 
	{
		logger.debug("getQualifiedName() - start");
		
		return getQualifiedName(this.schema, this.table, this.escapePattern);
	}

	/**
	 * Returns the qualified name using the values given in the constructor.
	 * The qualified table name is <b>only</b> returned if the feature
	 * {@link DatabaseConfig#FEATURE_QUALIFIED_TABLE_NAMES} is set. Otherwise the given
	 * name is returned unqualified (i.e. without prepending the prefix/schema).
	 * @return The qualified table name with the prepended schema if a schema is available.
	 * The qualified table name is <b>only</b> returned if the feature 
	 * {@link DatabaseConfig#FEATURE_QUALIFIED_TABLE_NAMES} is set in the given <code>config</code>.
	 */
	public String getQualifiedNameIfEnabled(DatabaseConfig config) 
	{
		logger.debug("getQualifiedNameIfEnabled(config={}) - start", config);

        boolean feature = config.getFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES);
        if (feature)
        {
        	logger.debug("Qualified table names feature is enabled. Returning qualified table name");
            return getQualifiedName(this.schema, this.table, this.escapePattern);
        }
        else 
        {
        	logger.debug("Qualified table names feature is disabled. Returning plain table name");
//        	return this.table;
        	return getQualifiedName(null, this.table, this.escapePattern);
        }
	}

	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(getClass().getName()).append("[");
		sb.append("schema=").append(schema);
		sb.append(", table=").append(table);
		sb.append("]");
		return sb.toString();
	}
	
	
	
    /**
     * Returns the specified name qualified with the specified prefix. The name
     * is not modified if the prefix is <code>null</code> or if the name is
     * already qualified.
     * <p>
     * Example: <br>
     * <code>getQualifiedName(null, "NAME")</code> returns
     * <code>"NAME"</code>. <code>getQualifiedName("PREFIX", "NAME")</code>
     * returns <code>"PREFIX.NAME"</code> and
     * <code>getQualifiedName("PREFIX2", "PREFIX1.NAME")</code>
     * returns <code>"PREFIX1.NAME"</code>.
     * 
     * @param prefix the prefix that qualifies the name and is prepended if the name is not qualified yet
     * @param name the name The name to be qualified if it is not qualified already
     * @param escapePattern The escape pattern to be applied on the prefix and the name. Can be null.
     * @return The qualified name
     */
    private String getQualifiedName(String prefix, String name,
            String escapePattern)
    {
        if(logger.isDebugEnabled())
            logger.debug("getQualifiedName(prefix={}, name={}, escapePattern={}) - start", 
                    new String[] {prefix, name, escapePattern});

        if (escapePattern != null)
        {
            prefix = getEscapedName(prefix, escapePattern);
            name = getEscapedName(name, escapePattern);
        }

        if (prefix == null || prefix.equals("") || name.indexOf(".") >= 0)
        {
            return name;
        }

        return prefix + "." + name;
    }
	
    
    /**
     * @param name
     * @param escapePattern
     * @return
     */
    private String getEscapedName(String name, String escapePattern)
    {
        logger.debug("getEscapedName(name={}, escapePattern={}) - start", name, escapePattern);

        if (name == null)
        {
            return name;
        }

        if (escapePattern == null) 
        {
            throw new NullPointerException(
                    "The parameter 'escapePattern' must not be null");
        }
        if(escapePattern.trim().equals(""))
        {
            throw new DatabaseUnitRuntimeException("Empty string is an invalid escape pattern!");
        }
    
        int split = name.indexOf(".");
        if (split > 1)
        {
        	return getEscapedName(name.substring(0, split), escapePattern) + "." + getEscapedName(name.substring(split + 1), escapePattern);
        }

        int index = escapePattern.indexOf("?");
        if (index >=0 )
        {
            String prefix = escapePattern.substring(0, index);
            String suffix = escapePattern.substring(index + 1);

            return prefix + name + suffix;
        }
        else if(escapePattern.length() == 1)
        {
            // No "?" in the escape pattern and only one character.
            // use the given escapePattern to surround the given name
            return escapePattern + name + escapePattern;
        }
        else
        {
            logger.warn("Invalid escape pattern '" + escapePattern + "'. Will not escape name '" + name + "'.");
            return name;
        }
    }

}