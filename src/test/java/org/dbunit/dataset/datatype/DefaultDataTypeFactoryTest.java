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

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Types;

import org.dbunit.dataset.datatype.ToleratedDeltaMap.ToleratedDelta;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Aug 13, 2003
 * @version $Revision$
 */
class DefaultDataTypeFactoryTest extends AbstractDataTypeFactoryTest
{

    @Override
    public IDataTypeFactory createFactory() throws Exception
    {
        return new DefaultDataTypeFactory();
    }

    @Test
    void testCreateNumberTolerantDataType_Numeric() throws Exception
    {
        final int sqlType = Types.NUMERIC;
        final String sqlTypeName = "NUMBER";

        final DefaultDataTypeFactory factory = new DefaultDataTypeFactory();
        factory.addToleratedDelta(
                new ToleratedDelta("TEST_TABLE", "COLUMN0", 1E-5));
        final DataType actual = factory.createDataType(sqlType, sqlTypeName,
                "TEST_TABLE", "COLUMN0");
        assertThat(actual.getClass()).as("type")
                .isEqualTo(NumberTolerantDataType.class);
        assertThat(((NumberTolerantDataType) actual).getToleratedDelta()
                .getDelta()).isEqualTo(new BigDecimal("1.0E-5"));
    }

    @Test
    void testCreateNumberTolerantDataType_Decimal() throws Exception
    {
        final int sqlType = Types.DECIMAL;
        final String sqlTypeName = "DECIMAL";

        final DefaultDataTypeFactory factory = new DefaultDataTypeFactory();
        factory.addToleratedDelta(
                new ToleratedDelta("TEST_TABLE", "COLUMN0", 1E-5));
        final DataType actual = factory.createDataType(sqlType, sqlTypeName,
                "TEST_TABLE", "COLUMN0");
        assertThat(actual.getClass()).as("type")
                .isEqualTo(NumberTolerantDataType.class);
        assertThat(((NumberTolerantDataType) actual).getToleratedDelta()
                .getDelta()).isEqualTo(new BigDecimal("1.0E-5"));
    }

    @Test
    void testCreateNumberTolerantDataTypeAndNoToleranceSetForColumn_Numeric()
            throws Exception
    {
        final int sqlType = Types.NUMERIC;
        final String sqlTypeName = "NUMBER";

        final DefaultDataTypeFactory factory = new DefaultDataTypeFactory();
        factory.addToleratedDelta(
                new ToleratedDelta("TEST_TABLE", "COLUMN0", 1E-5));
        final DataType actual = factory.createDataType(sqlType, sqlTypeName,
                "TEST_TABLE", "COLUMNXYZ-withoutTolerance");
        assertThat(actual).as("type").isSameAs(DataType.NUMERIC);
    }

    @Test
    void testCreateNumberTolerantDataTypeAndNoToleranceSetForColumn_Decimal()
            throws Exception
    {
        final int sqlType = Types.DECIMAL;
        final String sqlTypeName = "DECIMAL";

        final DefaultDataTypeFactory factory = new DefaultDataTypeFactory();
        factory.addToleratedDelta(
                new ToleratedDelta("TEST_TABLE", "COLUMN0", 1E-5));
        final DataType actual = factory.createDataType(sqlType, sqlTypeName,
                "TEST_TABLE", "COLUMNXYZ-withoutTolerance");
        assertThat(actual).as("type").isSameAs(DataType.DECIMAL);
    }

}
