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

package org.dbunit.ext.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Types;

import oracle.jdbc.OracleResultSet;
import oracle.sql.ORAData;
import oracle.sql.ORADataFactory;

import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.AbstractDataTypeTest;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author
 * @version
 */

@ExtendWith(MockitoExtension.class)
public class OracleSdoGeometryDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE =
            OracleDataTypeFactory.ORACLE_SDO_GEOMETRY_TYPE;

    @Mock
    private OracleResultSet mockedOracleResultSet;

    /**
     *
     */
    @Override
    @Test
    public void testToString_withDataType_returnsExpectedString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("SDO_GEOMETRY");
    }

    /**
     *
     */
    @Override
    @Test
    public void testGetTypeClass_returnsExpectedClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class")
                .isEqualTo(org.dbunit.ext.oracle.OracleSdoGeometry.class);
    }

    /**
     *
     */
    @Override
    @Test
    public void testIsNumber_returnsExpectedBoolean() throws Exception
    {
        assertThat(THIS_TYPE.isNumber()).as("is number").isFalse();
    }

    @Override
    @Test
    public void testIsDateTime_returnsExpectedBoolean() throws Exception
    {
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isFalse();
    }

    @Override
    @Test
    public void testTypeCast_withCompatibleInput_returnsExpectedValue() throws Exception
    {
        final Object[] values = {null,
                new OracleSdoGeometry(null, null, null, null, null),
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), null, null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(null, null, null), null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(new BigDecimal(1),
                                new BigDecimal(2), new BigDecimal(3)),
                        null, null),
                new OracleSdoGeometry(null, null, null,
                        new OracleSdoElemInfoArray(), null),
                new OracleSdoGeometry(null, null, null, null,
                        new OracleSdoOrdinateArray()),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("-1234.564"),
                                new BigDecimal("5.3403"), new BigDecimal(57)),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(10), new BigDecimal(9),
                                new BigDecimal(8), new BigDecimal(7)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {
                                new BigDecimal(6), new BigDecimal(5),
                                new BigDecimal(4), new BigDecimal(3)})),
                "NULL", " NULL ", "sdo_geometry(123, null, null, null, null)",
                "sdo_gEOMEtry(123, null, null, null, null)",
                "mdsys.sdo_geometry(123, 45.6, null, null, null)",
                "mdsys.sdo_geometry(123, 45.6, sdo_point_type(null,null,null),null,null)",
                "mdsys.sdo_geometry(123, 45.6, sdo_point_type(987.34,56.3,-123456),null,null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 0 ) ,null,null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 1 ) , sdo_elem_info_array(),null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 2 ) , mdsys.sdo_elem_info_array(null),null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 3 ) , sdo_elem_info_array(1,null,2,null,3,null),null)",
                "sdo_geometry(123, 45.6, mdsys.sdo_point_type ( 987.34 , 56.3 , 3 ) , mdsys.sdo_elem_info_array(1,2) , sdo_ordinate_array())",
                "sdo_geometry(123, 45.6, mdsys.sdo_point_type ( 987.34 , 56.3 , 3 ) , mdsys.sdo_elem_info_array(1,3) , sdo_ordinate_array(null))",
                " sdo_geometry(123, 45.6, mdsys.sdo_point_type (null,null,null) , mdsys.sdo_elem_info_array( 1 , 4 ) , MDSYS.sdo_ordinate_array( 4,5 , null , 6 ) ) ",};

        final OracleSdoGeometry[] expected = {null,
                new OracleSdoGeometry(null, null, null, null, null),
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), null, null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(null, null, null), null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(new BigDecimal(1),
                                new BigDecimal(2), new BigDecimal(3)),
                        null, null),
                new OracleSdoGeometry(null, null, null,
                        new OracleSdoElemInfoArray(), null),
                new OracleSdoGeometry(null, null, null, null,
                        new OracleSdoOrdinateArray()),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("-1234.564"),
                                new BigDecimal("5.3403"), new BigDecimal(57)),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(10), new BigDecimal(9),
                                new BigDecimal(8), new BigDecimal(7)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {
                                new BigDecimal(6), new BigDecimal(5),
                                new BigDecimal(4), new BigDecimal(3)})),
                null, null,
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), null, null, null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), new OracleSdoPointType(), null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"),
                                new BigDecimal("-123456")),
                        null, null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("0")),
                        null, null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("1")),
                        new OracleSdoElemInfoArray(), null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("2")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {null}),
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("3")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), null, new BigDecimal(2),
                                null, new BigDecimal(3), null}),
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("3")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), new BigDecimal(2)}),
                        new OracleSdoOrdinateArray()),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("3")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), new BigDecimal(3)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {null})),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(null, null, null),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), new BigDecimal(4)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {
                                new BigDecimal(4), new BigDecimal(5), null,
                                new BigDecimal(6)})),};

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(THIS_TYPE.typeCast(values[i])).as("typecast " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testTypeCastNone_withNullInput_returnsNull() throws Exception
    {
        assertThat(THIS_TYPE.typeCast(ITable.NO_VALUE)).as("typecast").isNull();
    }

    @Override
    @Test
    public void testTypeCastInvalid_withIncompatibleInput_throwsTypeCastException() throws Exception
    {
        final Object[] values = {new Object(), "bla", new java.util.Date(),
                "sdo_geometry(12xya3, null, null, null, null)",
                "sdo_geometry(, null, null, null, null)",
                "sdo_geometry(1,2, X, null, null)",
                "mdsys.sdo_geometry(123, 45.6, null, ABC, null)",
                "mdsys.sdo_geometry(123, 45.6, sdo_point_type(null,OUCH,null),null,null)",
                "mdsys.sdo_geometry(123, 45.6, sdo_point_type(987.34,56.3,-OUCH),null,null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 0 ) ,null,BAD)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 1 ) , sdo_elem_info_array,null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 2 ) , mdsys.sdo_elem_info_array(OUCH),null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 56.3 , 3 ) , sdo_elem_info_array(1,null,2,null,3,null),null)",
                " sdo_geometry(123, 45.6, mdsys.sdo_point_type (null,null,null) , mdsys.sdo_elem_info_array( 1 , 4 ) , MDSYS.sdo_ordinate_array( 4,5 , null , 6 ) , ) ",};

        for (int i = 0; i < values.length; i++)
        {
            try
            {
                THIS_TYPE.typeCast(values[i]);
                fail("Should throw TypeCastException");
            } catch (final TypeCastException e)
            {
            }
        }
    }

    @Override
    @Test
    public void testCompareEquals_withEqualValues_returnsZero() throws Exception
    {
        final Object[] values1 = {null,
                new OracleSdoGeometry(null, null, null, null, null),
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), null, null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(null, null, null), null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(new BigDecimal(1),
                                new BigDecimal(2), new BigDecimal(3)),
                        null, null),
                new OracleSdoGeometry(null, null, null,
                        new OracleSdoElemInfoArray(), null),
                new OracleSdoGeometry(null, null, null, null,
                        new OracleSdoOrdinateArray()),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("-1234.564"),
                                new BigDecimal("5.3403"), new BigDecimal(57)),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(10), new BigDecimal(9),
                                new BigDecimal(8), new BigDecimal(7)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {
                                new BigDecimal(6), new BigDecimal(5),
                                new BigDecimal(4), new BigDecimal(3)})),
                "NULL", " NULL ", "sdo_geometry(123, null, null, null, null)",
                "sdo_gEOMEtry(123, null, null, null, null)",
                "mdsys.sdo_geometry(123, 45.6, null, null, null)",
                "mdsys.sdo_geometry(123, 45.6, sdo_point_type(null,null,null),null,null)",
                "mdsys.sdo_geometry(123, 45.6, sdo_point_type(987.34,56.3,-123456),null,null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 0 ) ,null,null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 1 ) , sdo_elem_info_array(),null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 2 ) , mdsys.sdo_elem_info_array(null),null)",
                "mDSys.sdo_geometry(123, 45.6, mdsYS.sdo_point_type ( 987.34 , 56.3 , 3 ) , sdo_elem_info_array(1,null,2,null,3,null),null)",
                "sdo_geometry(123, 45.6, mdsys.sdo_point_type ( 987.34 , 56.3 , 3 ) , mdsys.sdo_elem_info_array(1,2) , sdo_ordinate_array())",
                "sdo_geometry(123, 45.6, mdsys.sdo_point_type ( 987.34 , 56.3 , 3 ) , mdsys.sdo_elem_info_array(1,3) , sdo_ordinate_array(null))",
                " sdo_geometry(123, 45.6, mdsys.sdo_point_type (null,null,null) , mdsys.sdo_elem_info_array( 1 , 4 ) , MDSYS.sdo_ordinate_array( 4,5 , null , 6 ) ) ",};

        final Object[] values2 = {null,
                new OracleSdoGeometry(null, null, null, null, null),
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), null, null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(null, null, null), null, null),
                new OracleSdoGeometry(null, null,
                        new OracleSdoPointType(new BigDecimal(1),
                                new BigDecimal(2), new BigDecimal(3)),
                        null, null),
                new OracleSdoGeometry(null, null, null,
                        new OracleSdoElemInfoArray(), null),
                new OracleSdoGeometry(null, null, null, null,
                        new OracleSdoOrdinateArray()),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("-1234.564"),
                                new BigDecimal("5.3403"), new BigDecimal(57)),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(10), new BigDecimal(9),
                                new BigDecimal(8), new BigDecimal(7)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {
                                new BigDecimal(6), new BigDecimal(5),
                                new BigDecimal(4), new BigDecimal(3)})),
                null, null,
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123), null, null, null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), null, null, null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"), new OracleSdoPointType(), null,
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"),
                                new BigDecimal("-123456")),
                        null, null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("0")),
                        null, null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("1")),
                        new OracleSdoElemInfoArray(), null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("2")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {null}),
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("3")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), null, new BigDecimal(2),
                                null, new BigDecimal(3), null}),
                        null),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("3")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), new BigDecimal(2)}),
                        new OracleSdoOrdinateArray()),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(new BigDecimal("987.34"),
                                new BigDecimal("56.3"), new BigDecimal("3")),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), new BigDecimal(3)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {null})),
                new OracleSdoGeometry(new BigDecimal(123),
                        new BigDecimal("45.6"),
                        new OracleSdoPointType(null, null, null),
                        new OracleSdoElemInfoArray(new BigDecimal[] {
                                new BigDecimal(1), new BigDecimal(4)}),
                        new OracleSdoOrdinateArray(new BigDecimal[] {
                                new BigDecimal(4), new BigDecimal(5), null,
                                new BigDecimal(6)})),};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            assertThat(THIS_TYPE.compare(values1[i], values2[i]))
                    .as("compare1 " + i).isZero();
            assertThat(THIS_TYPE.compare(values2[i], values1[i]))
                    .as("compare2 " + i).isZero();
        }
    }

    @Override
    @Test
    public void testCompareInvalid_withInvalidInput_throwsTypeCastException() throws Exception
    {
        final Object[] values1 = {new Object(), "bla", new java.util.Date()};
        final Object[] values2 = {null, null, null};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            try
            {
                THIS_TYPE.compare(values1[i], values2[i]);
                fail("Should throw TypeCastException");
            } catch (final TypeCastException e)
            {
            }

            try
            {
                THIS_TYPE.compare(values2[i], values1[i]);
                fail("Should throw TypeCastException");
            } catch (final TypeCastException e)
            {
            }
        }
    }

    @Override
    @Test
    public void testCompareDifferent_withDifferentValues_returnsNonZero() throws Exception
    {
        final Object[] less =
                {new OracleSdoGeometry(null, null, null, null, null),};

        final Object[] greater = {new OracleSdoGeometry(new BigDecimal(1),
                new BigDecimal(2), null, null, null),};

        assertThat(greater).as("values count").hasSameSizeAs(less);

        for (int i = 0; i < less.length; i++)
        {
            try
            {
                THIS_TYPE.compare(less[i], greater[i]);
                // OracleSdoGeometry objects are not Comparable
                fail("Should throw TypeCastException");
            } catch (final TypeCastException e)
            {
            }
        }
    }

    @Override
    @Test
    public void testSqlType_returnsExpectedSqlType() throws Exception
    {
        assertThat(THIS_TYPE.getSqlType()).isEqualTo(Types.STRUCT);
    }

    @Override
    @Test
    public void testForObject_withValidInput_returnsDataType() throws Exception
    {
        final DataType actual = DataType.forObject(new OracleSdoGeometry());
        assertThat(actual).isEqualTo(DataType.UNKNOWN);
    }

    @Override
    @Test
    public void testAsString_withValidInput_returnsStringRepresentation() throws Exception
    {
        // not supported until there is some way to render OracleSdoGeometry
        // as strings
        final Object[] values = {};

        final Object[] expected = {};

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(DataType.asString(values[i])).as("asString " + i)
                    .isEqualTo(expected[i]);
        }

    }

    @Override
    @Test
    public void testGetSqlValue_withValidStatement_returnsExpectedValue() throws Exception
    {
        when(mockedOracleResultSet.getORAData(eq(1), any(ORADataFactory.class))).thenReturn(null);
        assertThat(THIS_TYPE.getSqlValue(1, mockedOracleResultSet)).as("null ORAData returns NULL sentinel string")
                .isEqualTo("NULL");

        final String expectedString = "sdo_geometry(123, null, null, null, null)";
        final ORAData mockedData = org.mockito.Mockito.mock(ORAData.class);
        when(mockedData.toString()).thenReturn(expectedString);
        when(mockedOracleResultSet.getORAData(eq(1), any(ORADataFactory.class))).thenReturn(mockedData);
        assertThat(THIS_TYPE.getSqlValue(1, mockedOracleResultSet)).as("non-null ORAData returns its toString()")
                .isEqualTo(expectedString);
    }
}
