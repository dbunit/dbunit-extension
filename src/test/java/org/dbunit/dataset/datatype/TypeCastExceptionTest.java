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
package org.dbunit.dataset.datatype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @version $Revision$
 * @since 2.3.0
 */
class TypeCastExceptionTest
{

    @Test
    void testCreationWithNullValue()
    {
        final TypeCastException exception =
                new TypeCastException(null, DataType.BIGINT);
        assertThat(exception.getMessage()).isEqualTo(
                "Unable to typecast value <null> of type <null> to BIGINT");
    }

    @Test
    void testCreationWithNullDatatype()
    {
        final String value = "myStringObject";
        final TypeCastException exception =
                new TypeCastException(value, (DataType) null);
        assertThat(exception.getMessage())
                .isEqualTo("Unable to typecast value <" + value
                        + "> of type <java.lang.String> to null");
    }

}
