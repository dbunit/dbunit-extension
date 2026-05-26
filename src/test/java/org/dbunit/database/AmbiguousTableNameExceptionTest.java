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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.AbstractDbUnitExceptionTest;
import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AmbiguousTableNameException}.
 *
 * @since 3.2.0
 */
class AmbiguousTableNameExceptionTest
        extends AbstractDbUnitExceptionTest<AmbiguousTableNameException>
{
    @Override
    protected AmbiguousTableNameException createException()
    {
        return new AmbiguousTableNameException();
    }

    @Override
    protected AmbiguousTableNameException createException(final String message)
    {
        return new AmbiguousTableNameException(message);
    }

    @Override
    protected AmbiguousTableNameException createException(
            final String message, final Throwable cause)
    {
        return new AmbiguousTableNameException(message, cause);
    }

    @Override
    protected AmbiguousTableNameException createException(final Throwable cause)
    {
        return new AmbiguousTableNameException(cause);
    }

    @Test
    void testIsDataSetException_whenCreated_extendsDataSetException()
    {
        final AmbiguousTableNameException actual = new AmbiguousTableNameException("msg");

        assertThat(actual).as("is DataSetException.").isInstanceOf(DataSetException.class);
    }
}
