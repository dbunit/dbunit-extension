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
        extends AbstractDbUnitExceptionTest<DatabaseUnitRuntimeException>
{
    @Override
    protected DatabaseUnitRuntimeException createException()
    {
        return new DatabaseUnitRuntimeException();
    }

    @Override
    protected DatabaseUnitRuntimeException createException(final String message)
    {
        return new DatabaseUnitRuntimeException(message);
    }

    @Override
    protected DatabaseUnitRuntimeException createException(
            final String message, final Throwable cause)
    {
        return new DatabaseUnitRuntimeException(message, cause);
    }

    @Override
    protected DatabaseUnitRuntimeException createException(final Throwable cause)
    {
        return new DatabaseUnitRuntimeException(cause);
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
