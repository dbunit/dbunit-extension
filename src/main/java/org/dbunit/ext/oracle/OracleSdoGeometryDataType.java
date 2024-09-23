/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

package org.dbunit.ext.oracle;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.jdbc.OracleResultSet;
import oracle.jdbc.OraclePreparedStatement;
import oracle.sql.ORAData;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.dbunit.dataset.ITable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class implements DataType for Oracle SDO_GEOMETRY type used in Oracle Spatial. 
 * See the Oracle Spatial Developer's Guide for details on SDO_GEOMETRY.  This class
 * handles values similar to:
 * <ul>
 * <li>SDO_GEOMETRY(NULL, NULL, NULL, NULL, NULL)</li>
 * <li>NULL</li>
 * <li>SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(71.2988, 42.8052, NULL), NULL, NULL)</li>
 * <li>SDO_GEOMETRY(3302, NULL, SDO_POINT_TYPE(96.8233, 32.5261, NULL), SDO_ELEM_INFO_ARRAY(1, 2, 1), SDO_ORDINATE_ARRAY(2, 2, 0, 2, 4, 2, 8, 4, 8, 12, 4, 12, 12, 10, NULL, 8, 10, 22, 5, 14, 27))</li>
 * </ul>
 *
 * <p>
 * For more information on oracle spatial support go to http://tahiti.oracle.com
 * and search for &quot;spatial&quot;.  The developers guide is available at
 * http://download.oracle.com/docs/cd/B28359_01/appdev.111/b28400/toc.htm
 * </p>
 *
 * <p>
 * example table:
 * <code>
 *   CREATE TABLE cola_markets (
 *     mkt_id NUMBER PRIMARY KEY,
 *     name VARCHAR2(32),
 *     shape SDO_GEOMETRY);
 * </code>
 * </p>
 *
 * <p>
 * example insert:
 * <code>
 *   INSERT INTO cola_markets VALUES(
 *     2,
 *     'cola_b',
 *     SDO_GEOMETRY(
 *       2003,  -- two-dimensional polygon
 *       NULL,
 *       NULL,
 *       SDO_ELEM_INFO_ARRAY(1,1003,1), -- one polygon (exterior polygon ring)
 *       SDO_ORDINATE_ARRAY(5,1, 8,1, 8,6, 5,7, 5,1)
 *     )
 *    );
 * </code>
 * </p>
 *
 * <p>
 * This class uses the following objects which were rendered using oracle jpub and then
 * slightly customized to work with dbunit:
 * <ul>
 * <li>OracleSdoGeometry - corresponds to oracle SDO_GEOMETRY data type</li>
 * <li>OracleSdoPointType - corresponds to oracle SDO_POINT_TYPE data type</li>
 * <li>OracleSdoElemInfoArray - corresponds to oracle SDO_ELEM_INFO_ARRAY data type</li>
 * <li>OracleSdoOridinateArray - corresponds to oracle SDO_ORDINATE_ARRAY data type</li>
 * </ul>
 * These classes were rendered via jpub
 * (http://download.oracle.com/otn/utilities_drivers/jdbc/10201/jpub_102.zip)
 * with the following command syntax:
 * <code>
 * ./jpub -user=YOUR_USER_ID/YOUR_PASSWORD -url=YOUR_JDBC_URL
 *      -sql mdsys.sdo_geometry:OracleSdoGeometry,
 *            mdsys.sdo_point_type:OracleSdoPointType,
 *            mdsys.sdo_elem_info_array:OracleSdoElemInfoArray,
 *            mdsys.sdo_ordinate_array:OracleSdoOrdinateArray
 *      -dir=output_dir -methods=none -package=org.dbunit.ext.oracle -tostring=true
 * </code>
 * The equals and hashCode methods were then added so that the objects could be compared
 * in test cases. Note that I did have to bash the jpub startup script (change classpath)
 * because it assumes oracle 10g database but I ran it with 11g.  Theoretically, this
 * process can be repeated for other custom oracle object data types.
 * </p>
 *
 * @author clucas@e-miles.com
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since <dbunit-version>
 */
public class OracleSdoGeometryDataType extends AbstractDataType
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(OracleSdoGeometryDataType.class);

    private static final String NULL = "NULL";
    private static final String SDO_GEOMETRY = "SDO_GEOMETRY";

    // patterns for parsing out the various pieces of the string
    // representation of an sdo_geometry object
    private static final Pattern sdoGeometryPattern = Pattern.compile(
        "^(?:MDSYS\\.)?SDO_GEOMETRY\\s*\\(\\s*([^,\\s]+)\\s*,\\s*([^,\\s]+)\\s*,\\s*");
    private static final Pattern sdoPointTypePattern = Pattern.compile(
        "^(?:(?:(?:MDSYS\\.)?SDO_POINT_TYPE\\s*\\(\\s*([^,\\s]+)\\s*,\\s*([^,\\s]+)\\s*,\\s*([^,\\s\\)]+)\\s*\\))|(NULL))\\s*,\\s*");
    private static final Pattern sdoElemInfoArrayPattern = Pattern.compile(
        "^(?:(?:(?:(?:MDSYS\\.)?SDO_ELEM_INFO_ARRAY\\s*\\(([^\\)]*)\\))|(NULL)))\\s*,\\s*");
    private static final Pattern sdoOrdinateArrayPattern = Pattern.compile(
        "^(?:(?:(?:(?:MDSYS\\.)?SDO_ORDINATE_ARRAY\\s*\\(([^\\)]*)\\))|(NULL)))\\s*\\)\\s*");

    OracleSdoGeometryDataType ()
    {
        super(SDO_GEOMETRY, Types.STRUCT, OracleSdoGeometry.class, false);
    }

    public Object typeCast(Object value) throws TypeCastException
    {
        logger.debug("typeCast(value={}) - start", value);

        if (value == null || value == ITable.NO_VALUE)
        {
            return null;
        }


        if (value instanceof OracleSdoGeometry)
        {
            return (OracleSdoGeometry) value;
        }

        if (value instanceof String)
        {
            // attempt to parse the SDO_GEOMETRY
            try
            {
                // all upper case for parse purposes
                String upperVal = ((String) value).toUpperCase().trim();
                if (NULL.equals(upperVal))
                {
                    return null;
                }

                // parse out sdo_geometry
                Matcher sdoGeometryMatcher = sdoGeometryPattern.matcher(upperVal);
                if (! sdoGeometryMatcher.find())
                {
                    throw new TypeCastException(value, this);
                }
                BigDecimal gtype = NULL.equals(sdoGeometryMatcher.group(1)) ?
                    null : new BigDecimal(sdoGeometryMatcher.group(1));
                BigDecimal srid = NULL.equals(sdoGeometryMatcher.group(2)) ?
                    null : new BigDecimal(sdoGeometryMatcher.group(2));

                // parse out sdo_point_type
                upperVal = upperVal.substring(sdoGeometryMatcher.end());
                Matcher sdoPointTypeMatcher = sdoPointTypePattern.matcher(upperVal);
                if (! sdoPointTypeMatcher.find())
                {
                    throw new TypeCastException(value, this);
                }

                OracleSdoPointType sdoPoint;
                if (NULL.equals(sdoPointTypeMatcher.group(4)))
                {
                    sdoPoint = null;
                }
                else
                {
                    sdoPoint = new OracleSdoPointType(
                        NULL.equals(sdoPointTypeMatcher.group(1)) ? null :
                            new BigDecimal(sdoPointTypeMatcher.group(1)),
                        NULL.equals(sdoPointTypeMatcher.group(2)) ? null :
                            new BigDecimal(sdoPointTypeMatcher.group(2)),
                        NULL.equals(sdoPointTypeMatcher.group(3)) ? null :
                            new BigDecimal(sdoPointTypeMatcher.group(3)));
                }

                // parse out sdo_elem_info_array
                upperVal = upperVal.substring(sdoPointTypeMatcher.end());
                Matcher sdoElemInfoArrayMatcher = sdoElemInfoArrayPattern.matcher(upperVal);
                if (! sdoElemInfoArrayMatcher.find())
                {
                    throw new TypeCastException(value, this);
                }

                OracleSdoElemInfoArray sdoElemInfoArray;
                if (NULL.equals(sdoElemInfoArrayMatcher.group(2)))
                {
                    sdoElemInfoArray = null;
                }
                else
                {
                    String [] elemInfoStrings = sdoElemInfoArrayMatcher.group(1).
                        trim().split("\\s*,\\s*");
                    if (elemInfoStrings.length == 1 && "".equals(elemInfoStrings[0]))
                    {
                        sdoElemInfoArray = new OracleSdoElemInfoArray();
                    }
                    else
                    {
                        BigDecimal [] elemInfos = new BigDecimal[elemInfoStrings.length];
                        for (int index = 0; index < elemInfoStrings.length; index++)
                        {
                            elemInfos[index] = NULL.equals(elemInfoStrings[index]) ?
                                null : new BigDecimal(elemInfoStrings[index]);
                        }
                        sdoElemInfoArray = new OracleSdoElemInfoArray(elemInfos);
                    }
                }

                // parse out sdo_ordinate_array
                upperVal = upperVal.substring(sdoElemInfoArrayMatcher.end());
                Matcher sdoOrdinateArrayMatcher = sdoOrdinateArrayPattern.matcher(upperVal);
                if (! sdoOrdinateArrayMatcher.find())
                {
                    throw new TypeCastException(value, this);
                }

                OracleSdoOrdinateArray sdoOrdinateArray;
                if (NULL.equals(sdoOrdinateArrayMatcher.group(2)))
                {
                    sdoOrdinateArray = null;
                }
                else
                {
                    String [] ordinateStrings = sdoOrdinateArrayMatcher.group(1).
                        trim().split("\\s*,\\s*");
                    if (ordinateStrings.length == 1 && "".equals(ordinateStrings[0]))
                    {
                        sdoOrdinateArray = new OracleSdoOrdinateArray();
                    }
                    else
                    {
                        BigDecimal [] ordinates = new BigDecimal[ordinateStrings.length];
                        for (int index = 0; index < ordinateStrings.length; index++)
                        {
                            ordinates[index] = NULL.equals(ordinateStrings[index]) ?
                                null : new BigDecimal(ordinateStrings[index]);
                        }
                        sdoOrdinateArray = new OracleSdoOrdinateArray(ordinates);
                    }
                }

                OracleSdoGeometry sdoGeometry = new OracleSdoGeometry(
                    gtype, srid, sdoPoint, sdoElemInfoArray, sdoOrdinateArray);

                return sdoGeometry;
            }
            catch (SQLException e)
            {
                throw new TypeCastException(value, this, e);
            }
            catch (NumberFormatException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        throw new TypeCastException(value, this);
    }


    public Object getSqlValue(int column, ResultSet resultSet)
        throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
            resultSet);

        Object data = null;
        try
        {
            data =  ((OracleResultSet) resultSet).
                getORAData(column, OracleSdoGeometry.getORADataFactory());

            // It would be preferable to return the actual object, but there are
            // a few dbunit issues with this:
            //
            // 1. Dbunit does not support nulls for user defined types (at least
            //    with oracle.)  PreparedStatement.setNull(int, int) is always used
            //    but PreparedStatement.setNull(int, int, String) is required
            //    for sdo_geometry (and other similar custom object types).
            //
            // 2. Dbunit does not support rendering custom objects (such as
            //    OracleSdoGeometry) as strings.
            //
            // So, instead we return the object as a String or "NULL".

            // return data;

            if (data != null)
            {
                return data.toString();
            }
            else
            {
                // return a string instead of null so that it can be interpreted
                // in typeCast.  DBUnit does not handle PreparedStatement.setNull
                // for user defined types.
                return NULL;
            }

        }
        catch (SQLException e)
        {
            throw new TypeCastException(data, this, e);
        }
    }

    public void setSqlValue(Object value, int column, PreparedStatement statement)
        throws SQLException, TypeCastException
    {
        Object castValue = typeCast(value);
        if (castValue == null)
        {
            statement.setNull(column, OracleSdoGeometry._SQL_TYPECODE,
                OracleSdoGeometry._SQL_NAME);
        }
        else
        {
            ((OraclePreparedStatement) statement).setORAData(column, (ORAData) castValue);
        }
    }

    /**
     * This method is copied from AbstractDataType and customized to call equals
     * after the typeCast because OracleSdoGeometry objects are not Comparables
     * but can test for equality (via equals method.)  It is needed for test
     * cases that check for equality between data in xml files and data read
     * from the database.
     */
    public int compare(Object o1, Object o2) throws TypeCastException
    {
        logger.debug("compare(o1={}, o2={}) - start", o1, o2);

        try
        {
            // New in 2.3: Object level check for equality - should give massive performance improvements
            // in the most cases because the typecast can be avoided (null values and equal objects)
            if(areObjectsEqual(o1, o2))
            {
                return 0;
            }


            // Comparable check based on the results of method "typeCast"
            Object value1 = typeCast(o1);
            Object value2 = typeCast(o2);

            // Check for "null"s again because typeCast can produce them

            if (value1 == null && value2 == null)
            {
                return 0;
            }

            if (value1 == null && value2 != null)
            {
                return -1;
            }

            if (value1 != null && value2 == null)
            {
                return 1;
            }

            if (value1.equals(value2))
            {
                return 0;
            }

            return compareNonNulls(value1, value2);

        }
        catch (ClassCastException e)
        {
            throw new TypeCastException(e);
        }
    }

}
