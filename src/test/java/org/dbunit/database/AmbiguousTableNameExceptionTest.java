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

import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AmbiguousTableNameException}.
 *
 * @since 3.2.0
 */
class AmbiguousTableNameExceptionTest
{
    @Test
    void testNoArgConstructor_withNoArgs_createsExceptionWithNullMessage()
    {
        final AmbiguousTableNameException actual = new AmbiguousTableNameException();

        assertThat(actual).as("exception instance.").isNotNull();
        assertThat(actual.getMessage()).as("message.").isNull();
        assertThat(actual.getCause()).as("cause.").isNull();
    }

    @Test
    void testMessageConstructor_withMessage_storesMessage()
    {
        final String message = "Table 'MY_TABLE' is ambiguous";
        final AmbiguousTableNameException actual = new AmbiguousTableNameException(message);

        assertThat(actual.getMessage()).as("message.").isEqualTo(message);
        assertThat(actual.getCause()).as("cause.").isNull();
    }

    @Test
    void testMessageAndCauseConstructor_withMessageAndCause_storesBoth()
    {
        final String message = "Ambiguous table detected";
        final Throwable cause = new RuntimeException("underlying problem");
        final AmbiguousTableNameException actual = new AmbiguousTableNameException(message, cause);

        assertThat(actual.getMessage()).as("message.").isEqualTo(message);
        assertThat(actual.getCause()).as("cause.").isSameAs(cause);
    }

    @Test
    void testCauseOnlyConstructor_withCause_wrapsThrowable()
    {
        final Throwable cause = new IllegalStateException("root cause");
        final AmbiguousTableNameException actual = new AmbiguousTableNameException(cause);

        assertThat(actual.getCause()).as("cause.").isSameAs(cause);
        assertThat(actual.getMessage()).as("message contains cause class.").contains("IllegalStateException");
    }

    @Test
    void testIsDataSetException_whenCreated_extendsDataSetException()
    {
        final AmbiguousTableNameException actual = new AmbiguousTableNameException("msg");

        assertThat(actual).as("is DataSetException.").isInstanceOf(DataSetException.class);
    }
}
