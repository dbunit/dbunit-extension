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
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.statement.IStatementFactory;
import org.dbunit.database.statement.PreparedStatementFactory;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.dataset.filter.IColumnFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration used by the {@link DatabaseConnection}.
 * 
 * @author manuel.laflamme
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.0
 */
public class DatabaseConfig
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    /** Name of the property configuring the {@link IStatementFactory} implementation to use. */
    public static final String PROPERTY_STATEMENT_FACTORY =
            "http://www.dbunit.org/properties/statementFactory";
    /** Name of the property configuring the {@link IResultSetTableFactory} implementation to use. */
    public static final String PROPERTY_RESULTSET_TABLE_FACTORY =
            "http://www.dbunit.org/properties/resultSetTableFactory";
    /** Name of the property configuring the {@link IDataTypeFactory} implementation to use. */
    public static final String PROPERTY_DATATYPE_FACTORY =
            "http://www.dbunit.org/properties/datatypeFactory";
    /** Name of the property configuring the pattern used to escape table and column names. */
    public static final String PROPERTY_ESCAPE_PATTERN =
            "http://www.dbunit.org/properties/escapePattern";
    /** Name of the property configuring the JDBC table types considered when reading the database schema. */
    public static final String PROPERTY_TABLE_TYPE =
            "http://www.dbunit.org/properties/tableType";
    /** Name of the property configuring the {@link IColumnFilter} used to determine primary key columns. */
    public static final String PROPERTY_PRIMARY_KEY_FILTER =
            "http://www.dbunit.org/properties/primaryKeyFilter";
    /** Name of the property configuring the batch size used for batched statements. */
    public static final String PROPERTY_BATCH_SIZE =
    		"http://www.dbunit.org/properties/batchSize";
	/** Name of the property configuring the JDBC fetch size used for result sets. */
	public static final String PROPERTY_FETCH_SIZE =
			"http://www.dbunit.org/properties/fetchSize";
	/** Name of the property configuring the {@link IMetadataHandler} implementation to use. */
	public static final String PROPERTY_METADATA_HANDLER =
	        "http://www.dbunit.org/properties/metadataHandler";
	/**
	 * Name of the property configuring whether verifying a table definition allows the expected
	 * table to have a different column count.
	 */
	public static final String PROPERTY_ALLOW_VERIFYTABLEDEFINITION_EXPECTEDTABLE_COUNT_MISMATCH =
	        "http://www.dbunit.org/properties/allowVerifytabledefinitionExpectedtableCountMismatch";
    /** Name of the property configuring the {@link IColumnFilter} used to determine MS SQL identity columns. */
    public static final String PROPERTY_IDENTITY_COLUMN_FILTER =
            "http://www.dbunit.org/properties/mssql/identityColumnFilter";

    /** Name of the feature controlling whether table names are treated as case sensitive. */
    public static final String FEATURE_CASE_SENSITIVE_TABLE_NAMES =
        "http://www.dbunit.org/features/caseSensitiveTableNames";
    /** Name of the feature controlling whether table names are qualified with the schema name. */
    public static final String FEATURE_QUALIFIED_TABLE_NAMES =
        "http://www.dbunit.org/features/qualifiedTableNames";
    /** Name of the feature controlling whether batched statements are used. */
    public static final String FEATURE_BATCHED_STATEMENTS =
        "http://www.dbunit.org/features/batchedStatements";
    /** Name of the feature controlling whether unsupported data type warnings are logged. */
    public static final String FEATURE_DATATYPE_WARNING =
        "http://www.dbunit.org/features/datatypeWarning";
    /** Name of the feature controlling whether Oracle recycle bin tables are skipped. */
    public static final String FEATURE_SKIP_ORACLE_RECYCLEBIN_TABLES =
        "http://www.dbunit.org/features/skipOracleRecycleBinTables";
    /** Name of the feature controlling whether empty fields are allowed in flat XML datasets. */
    public static final String FEATURE_ALLOW_EMPTY_FIELDS =
            "http://www.dbunit.org/features/allowEmptyFields";
    /** Name of the feature controlling whether all columns are used for sorting when a table has no primary key. */
    public static final String FEATURE_SORT_ALL_COLUMNS_WHEN_NO_PRIMARY_KEY =
            "http://www.dbunit.org/features/sortAllColumnsWhenNoPrimaryKey";

    /**
     * A list of all properties as {@link ConfigProperty} objects. 
     * The objects contain the allowed java type and whether or not a property is nullable.
     */
    public static final ConfigProperty[] ALL_PROPERTIES = new ConfigProperty[] {
        new ConfigProperty(PROPERTY_STATEMENT_FACTORY, IStatementFactory.class, false),
        new ConfigProperty(PROPERTY_RESULTSET_TABLE_FACTORY, IResultSetTableFactory.class, false),
        new ConfigProperty(PROPERTY_DATATYPE_FACTORY, IDataTypeFactory.class, false),
        new ConfigProperty(PROPERTY_ESCAPE_PATTERN, String.class, true),
        new ConfigProperty(PROPERTY_TABLE_TYPE, String[].class, false),
        new ConfigProperty(PROPERTY_PRIMARY_KEY_FILTER, IColumnFilter.class, true),
        new ConfigProperty(PROPERTY_BATCH_SIZE, Integer.class, false),
        new ConfigProperty(PROPERTY_FETCH_SIZE, Integer.class, false),
        new ConfigProperty(PROPERTY_METADATA_HANDLER, IMetadataHandler.class, false),
        new ConfigProperty(PROPERTY_IDENTITY_COLUMN_FILTER, IColumnFilter.class, true),
        new ConfigProperty(FEATURE_CASE_SENSITIVE_TABLE_NAMES, Boolean.class, false),
        new ConfigProperty(FEATURE_QUALIFIED_TABLE_NAMES, Boolean.class, false),
        new ConfigProperty(FEATURE_BATCHED_STATEMENTS, Boolean.class, false),
        new ConfigProperty(FEATURE_DATATYPE_WARNING, Boolean.class, false),
        new ConfigProperty(FEATURE_SKIP_ORACLE_RECYCLEBIN_TABLES, Boolean.class, false),
        new ConfigProperty(FEATURE_ALLOW_EMPTY_FIELDS, Boolean.class, false),
        new ConfigProperty(PROPERTY_ALLOW_VERIFYTABLEDEFINITION_EXPECTEDTABLE_COUNT_MISMATCH, Boolean.class, false),
        new ConfigProperty(FEATURE_SORT_ALL_COLUMNS_WHEN_NO_PRIMARY_KEY, Boolean.class, false),
    };

    /**
     * A list of all features as strings
     * @deprecated since 2.4.7 Use the {@link #ALL_PROPERTIES} where features are listed now as well
     */
    public static final String[] ALL_FEATURES = new String[] {
        FEATURE_CASE_SENSITIVE_TABLE_NAMES,
        FEATURE_QUALIFIED_TABLE_NAMES,
        FEATURE_BATCHED_STATEMENTS,
        FEATURE_DATATYPE_WARNING,
        FEATURE_SKIP_ORACLE_RECYCLEBIN_TABLES,
        FEATURE_ALLOW_EMPTY_FIELDS,
        FEATURE_SORT_ALL_COLUMNS_WHEN_NO_PRIMARY_KEY
    };
    
    private static final DefaultDataTypeFactory DEFAULT_DATA_TYPE_FACTORY =
            new DefaultDataTypeFactory();
    private static final PreparedStatementFactory PREPARED_STATEMENT_FACTORY =
            new PreparedStatementFactory();
    private static final CachedResultSetTableFactory RESULT_SET_TABLE_FACTORY =
            new CachedResultSetTableFactory();
    private static final String DEFAULT_ESCAPE_PATTERN = null;
    private static final String[] DEFAULT_TABLE_TYPE = {"TABLE"};
    private static final Integer DEFAULT_BATCH_SIZE = 100;
    private static final Integer DEFAULT_FETCH_SIZE = 100;



    private Map _propertyMap = new HashMap();
    
    private final Configurator configurator;

    /**
     * Constructs a database config with the framework's default properties and features.
     */
    public DatabaseConfig()
    {
        setFeature(FEATURE_BATCHED_STATEMENTS, false);
        setFeature(FEATURE_QUALIFIED_TABLE_NAMES, false);
        setFeature(FEATURE_CASE_SENSITIVE_TABLE_NAMES, false);
        setFeature(FEATURE_DATATYPE_WARNING, true);
        setFeature(FEATURE_ALLOW_EMPTY_FIELDS, false);
        setFeature(FEATURE_SORT_ALL_COLUMNS_WHEN_NO_PRIMARY_KEY, false);

        setProperty(PROPERTY_STATEMENT_FACTORY, PREPARED_STATEMENT_FACTORY);
        setProperty(PROPERTY_RESULTSET_TABLE_FACTORY, RESULT_SET_TABLE_FACTORY);
        setProperty(PROPERTY_DATATYPE_FACTORY, DEFAULT_DATA_TYPE_FACTORY);
        setProperty(PROPERTY_ESCAPE_PATTERN, DEFAULT_ESCAPE_PATTERN);
        setProperty(PROPERTY_TABLE_TYPE, DEFAULT_TABLE_TYPE);
        setProperty(PROPERTY_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        setProperty(PROPERTY_FETCH_SIZE, DEFAULT_FETCH_SIZE);
        setProperty(PROPERTY_METADATA_HANDLER, new DefaultMetadataHandler());
        setProperty(
                PROPERTY_ALLOW_VERIFYTABLEDEFINITION_EXPECTEDTABLE_COUNT_MISMATCH,
                Boolean.FALSE);

        this.configurator = new Configurator(this);
    }

    /**
     * Returns the configurator of this database config.
     *
     * @return The configurator of this database config
     */
    protected Configurator getConfigurator()
    {
        return configurator;
    }

    /**
     * Set the value of a feature flag.
     *
     * @param name the feature id
     * @param value the feature status
     * @deprecated since 2.4.7 Use the {@link #setProperty(String, Object)} also for features
     */
    public void setFeature(String name, boolean value)
    {
        logger.trace("setFeature(name={}, value={}) - start", name, String.valueOf(value));

        setProperty(name, Boolean.valueOf(value));
    }

    /**
     * Look up the value of a feature flag.
     *
     * @param name the feature id
     * @return the feature status
     * @deprecated since 2.4.7 Use the {@link #getProperty(String)} where features are listed now as well
     */
    public boolean getFeature(String name)
    {
        logger.trace("getFeature(name={}) - start", name);
        
        Object property = getProperty(name);
        if(property == null)
        {
            return false;
        }
        else if(property instanceof Boolean)
        {
            Boolean feature = (Boolean) property;
            return feature.booleanValue();
        }
        else
        {
            String propString = String.valueOf(property);
            Boolean feature = Boolean.valueOf(propString);
            return feature.booleanValue();
        }
    }

    /**
     * Set the value of a property.
     *
     * @param name the property id
     * @param value the property value
     */
    public void setProperty(String name, Object value)
    {
        logger.trace("setProperty(name={}, value={}) - start", name, value);
        
        value = convertIfNeeded(name, value);
        
        // Validate if the type of the given object is correct
        checkObjectAllowed(name, value);
        
        // If we get here the type is allowed (no exception was thrown)
        _propertyMap.put(name, value);
    }

    /**
     * Look up the value of a property.
     *
     * @param name the property id
     * @return the property value
     */
    public Object getProperty(String name)
    {
        logger.trace("getProperty(name={}) - start", name);

        return _propertyMap.get(name);
    }

    private Object convertIfNeeded(String property, Object value) 
    {
        logger.trace("convertIfNeeded(property={}, value={}) - start", property, value);

        ConfigProperty prop = findByName(property);
        if(prop==null) {
            throw new IllegalArgumentException("Did not find property with name '" + property + "'");
        }
        Class allowedPropType = prop.getPropertyType();

        if(allowedPropType == Boolean.class || allowedPropType == boolean.class)
        {
            // String -> Boolean is a special mapping which is allowed
            if(value instanceof String)
            {
                return Boolean.valueOf((String)value);
            }
        }
        
        return value;
    }

    /**
     * Checks whether the given value has the correct java type for the given property.
     * If the value is not allowed for the given property an {@link IllegalArgumentException} is thrown.
     * @param property The property to be set
     * @param value The value to which the property should be set
     */
    protected void checkObjectAllowed(String property, Object value)
    {
        logger.trace("checkObjectAllowed(property={}, value={}) - start", property, value);

        ConfigProperty prop = findByName(property);
        
        if(prop != null)
        {
            // First check for null
            if(value == null)
            {
                if(prop.isNullable())
                {
                    // All right. No class check is needed
                    return;
                }
                else
                {
                    throw new IllegalArgumentException("The property '" + property + "' is not nullable.");
                }
            }
            else
            {
                Class allowedPropType = prop.getPropertyType();
                if(!allowedPropType.isAssignableFrom(value.getClass()))
                {
                    throw new IllegalArgumentException("Cannot cast object of type '" + value.getClass() + 
                            "' to allowed type '" + allowedPropType + "'.");
                }
            }
        }
        else
        {
            logger.info("Unknown property '" + property + "'. Cannot validate the type of the object to be set." +
                    " Please notify a developer to update the list of properties.");
        }
    }
    
    /**
     * Sets the given properties on the {@link DatabaseConfig} instance using the given String values.
     * This is useful to set properties configured as strings by a build tool like ant or maven. 
     * If the required property type is an object it uses reflection to create an instance of the class
     * specified as string.
     * @param stringProperties The properties as strings. The key of the properties can be either the long or
     * the short name.
     * @throws DatabaseUnitException if a string value cannot be converted to its required property type.
     */
    public void setPropertiesByString(Properties stringProperties) throws DatabaseUnitException
    {
        for (Iterator iterator = stringProperties.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            
            String propKey = (String)entry.getKey();
            String propValue = (String)entry.getValue();

            ConfigProperty dbunitProp = DatabaseConfig.findByName(propKey);
            if(dbunitProp == null)
            {
                logger.debug("Did not find long name property {} - trying short name...", entry);
                dbunitProp = DatabaseConfig.findByShortName(propKey);
            }

            if(dbunitProp == null)
            {
                logger.info("Could not set property '{}' - not found in the list of known properties.", entry);
            }
            else
            {
                String fullPropName = dbunitProp.getProperty();
                Object obj = createObjectFromString(dbunitProp, propValue);
                this.setProperty(fullPropName, obj);
            }
        }
    }
    
    private Object createObjectFromString(ConfigProperty dbunitProp, String propValue) 
    throws DatabaseUnitException 
    {
        if (dbunitProp == null) {
            throw new NullPointerException(
                    "The parameter 'dbunitProp' must not be null");
        }
        if (propValue == null) {
            // Null must not be casted
            return null;
        }
        
        Class targetClass = dbunitProp.getPropertyType();
        if(targetClass == String.class)
        {
            return propValue;
        }
        else if(targetClass == Boolean.class)
        {
            return Boolean.valueOf(propValue);
        }
        else if(targetClass == String[].class)
        {
            String[] result = propValue.split(",");
            for (int i = 0; i < result.length; i++) {
                result[i] = result[i].trim();
            }
            return result;
        }
        else if(targetClass == Integer.class)
        {
            return new Integer(propValue);
        }
        else
        {
            // Try via reflection
            return createInstance(propValue);
        }
    }

    private Object createInstance(String className) throws DatabaseUnitException 
    {
        // Setup data type factory for example.
        try
        {
            Object o = Class.forName(className).newInstance();
            return o;
        }
        catch (ClassNotFoundException e)
        {
            throw new DatabaseUnitException(
                    "Class Not Found: '" + className + "' could not be loaded", e);
        }
        catch (IllegalAccessException e)
        {
            throw new DatabaseUnitException(
                    "Illegal Access: '" + className + "' could not be loaded", e);
        }
        catch (InstantiationException e)
        {
            throw new DatabaseUnitException(
                    "Instantiation Exception: '" + className + "' could not be loaded", e);
        }
    }

    /**
     * Searches the {@link ConfigProperty} object for the property with the given name
     * @param property The property for which the enumerated object should be resolved
     * @return The property object or <code>null</code> if it was not found.
     */
    public static final ConfigProperty findByName(String property) 
    {
        for (int i = 0; i < ALL_PROPERTIES.length; i++) {
            if(ALL_PROPERTIES[i].getProperty().equals(property))
            {
                return ALL_PROPERTIES[i];
            }
        }
        // property not found.
        return null;
    }
    
    /**
     * Searches the {@link ConfigProperty} object for the property with the given name
     * @param propShortName The property short name for which the enumerated object should be resolved.
     * Example: the short name of {@value #PROPERTY_FETCH_SIZE} is <code>fetchSize</code> which is the
     * last part of the fully qualified URL.
     * @return The property object or <code>null</code> if it was not found.
     */
    public static final ConfigProperty findByShortName(String propShortName) 
    {
        for (int i = 0; i < DatabaseConfig.ALL_PROPERTIES.length; i++) {
            String fullProperty = DatabaseConfig.ALL_PROPERTIES[i].getProperty();
            if(fullProperty.endsWith(propShortName))
            {
                return DatabaseConfig.ALL_PROPERTIES[i];
            }
        }
        // Property not found
        logger.info("The property ending with '" + propShortName + "' was not found. " +
                "Please notify a dbunit developer to add the property to the " + DatabaseConfig.class);
        return null;
    }

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append(", _propertyMap=").append(_propertyMap);
        sb.append("]");
        return sb.toString();
    }
    

    
    
    /**
     * Descriptor for a single named, typed, nullable {@link DatabaseConfig} property.
     *
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.4.0
     */
    public static class ConfigProperty
    {
        private String property;
        private Class propertyType;
        private boolean nullable;
        
        /**
         * Creates a descriptor for a configuration property.
         *
         * @param property the property name.
         * @param propertyType the allowed java type of the property's value.
         * @param nullable whether the property's value may be <code>null</code>.
         */
        public ConfigProperty(String property, Class propertyType, boolean nullable) {
            super();
            
            if (property == null) {
                throw new NullPointerException(
                        "The parameter 'property' must not be null");
            }
            if (propertyType == null) {
                throw new NullPointerException(
                        "The parameter 'propertyType' must not be null");
            }
            
            this.property = property;
            this.propertyType = propertyType;
            this.nullable = nullable;
        }
        
        /**
         * Returns the property name.
         * @return the property name.
         */
        public String getProperty() {
            return property;
        }

        /**
         * Returns the allowed java type of the property's value.
         * @return the allowed java type of the property's value.
         */
        public Class getPropertyType() {
            return propertyType;
        }

        /**
         * Returns whether the property's value may be <code>null</code>.
         * @return <code>true</code> if the property's value may be <code>null</code>.
         */
        public boolean isNullable() {
            return nullable;
        }

        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((property == null) ? 0 : property.hashCode());
            return result;
        }

        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConfigProperty other = (ConfigProperty) obj;
            if (property == null) {
                if (other.property != null)
                    return false;
            } else if (!property.equals(other.property))
                return false;
            return true;
        }

        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName()).append("[");
            sb.append("property=").append(property);
            sb.append(", propertyType=").append(propertyType);
            sb.append(", nullable=").append(nullable);
            sb.append("]");
            return sb.toString();
        }
    }
    
    
    
    /**
     * Sets parameters stored in the {@link DatabaseConfig} on specific java objects like {@link Statement}.
     * Is mainly there to avoid code duplication where {@link DatabaseConfig} parameters are used.
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.4.4
     */
    protected static class Configurator
    {
        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(Configurator.class);

        private DatabaseConfig config;
        
        /**
         * Creates a configurator backed by the given database config.
         *
         * @param config The configuration to be used by this configurator
         * @since 2.4.4
         */
        public Configurator(DatabaseConfig config)
        {
            if (config == null) {
                throw new NullPointerException(
                        "The parameter 'config' must not be null");
            }
            this.config = config;
        }
        /**
         * Configures the given statement so that it has the properties that are configured in this {@link DatabaseConfig}.
         * @param stmt The statement to be configured.
         * @throws SQLException
         * @since 2.4.4
         */
        void configureStatement(Statement stmt) throws SQLException 
        {
            logger.trace("configureStatement(stmt={}) - start", stmt);
            Integer fetchSize = (Integer) config.getProperty(DatabaseConfig.PROPERTY_FETCH_SIZE);
            stmt.setFetchSize(fetchSize.intValue());
            logger.debug("Statement fetch size set to {}",fetchSize);
        }
        
    }

}
