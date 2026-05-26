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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DatabaseUnitRuntimeException}.
 *
 * @author DbUnit.org
 * @since 1.0
 */
class DatabaseUnitRuntimeExceptionTest
{
    @Test
    void testConstructor_noArgs_createsExceptionWithNullMessage()
    {
        final DatabaseUnitRuntimeException ex = new DatabaseUnitRuntimeException();
        assertThat(ex.getMessage())
                .as("No-arg constructor should produce an exception with null message.")
                .isNull();
        assertThat(ex.getCause())
                .as("No-arg constructor should produce an exception with null cause.")
                .isNull();
    }

    @Test
    void testConstructor_withMessage_setsMessage()
    {
        final String message = "runtime error occurred";
        final DatabaseUnitRuntimeException ex = new DatabaseUnitRuntimeException(message);
        assertThat(ex.getMessage())
                .as("Constructor with message should store the message.")
                .isEqualTo(message);
        assertThat(ex.getCause())
                .as("Constructor with message only should have null cause.")
                .isNull();
    }

    @Test
    void testConstructor_withMessageAndCause_setsBoth()
    {
        final String message = "wrapped error";
        final IllegalStateException cause = new IllegalStateException("underlying cause");
        final DatabaseUnitRuntimeException ex = new DatabaseUnitRuntimeException(message, cause);
        assertThat(ex.getMessage())
                .as("Constructor with message and cause should store the message.")
                .isEqualTo(message);
        assertThat(ex.getCause())
                .as("Constructor with message and cause should store the cause.")
                .isEqualTo(cause);
    }

    @Test
    void testConstructor_withCauseOnly_setsMessageFromCauseToString()
    {
        final IllegalArgumentException cause = new IllegalArgumentException("bad argument");
        final DatabaseUnitRuntimeException ex = new DatabaseUnitRuntimeException(cause);
        assertThat(ex.getCause())
                .as("Constructor with cause only should store the cause.")
                .isEqualTo(cause);
        assertThat(ex.getMessage())
                .as("Constructor with cause only should use cause.toString() as message.")
                .isEqualTo(cause.toString());
    }

    @Test
    void testIsRuntimeException_onInstance_isAssignableFromRuntimeException()
    {
        final DatabaseUnitRuntimeException ex =
                new DatabaseUnitRuntimeException("test");
        assertThat(ex)
                .as("DatabaseUnitRuntimeException should be a RuntimeException.")
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("deprecation")
    @Test
    void testGetException_withCause_returnsCause()
    {
        final RuntimeException cause = new RuntimeException("original");
        final DatabaseUnitRuntimeException ex =
                new DatabaseUnitRuntimeException("msg", cause);
        assertThat(ex.getException())
                .as("getException() should delegate to getCause().")
                .isEqualTo(cause);
    }

    @SuppressWarnings("deprecation")
    @Test
    void testGetException_withNoCause_returnsNull()
    {
        final DatabaseUnitRuntimeException ex =
                new DatabaseUnitRuntimeException("no cause");
        assertThat(ex.getException())
                .as("getException() should return null when no cause is set.")
                .isNull();
    }
}
