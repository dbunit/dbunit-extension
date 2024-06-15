/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2008, DbUnit.org
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
package org.dbunit.ext.h2;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataTypeFactoryTest;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.1
 */
class H2DataTypeFactoryTest extends AbstractDataTypeFactoryTest
{
    @Override
    public IDataTypeFactory createFactory() throws Exception
    {
        return new H2DataTypeFactory();
    }

    @Test
    void testCreateBooleanDataType() throws Exception
    {
        final int sqlType = Types.BIT;
        final String sqlTypeName = "BOOLEAN";

        final DataType expected = DataType.BOOLEAN;
        final DataType actual =
                createFactory().createDataType(sqlType, sqlTypeName);
        assertThat(actual).as("type").isSameAs(expected);
    }

}
