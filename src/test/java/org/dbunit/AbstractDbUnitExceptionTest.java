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
 * Abstract base for unit tests of DbUnit exception types that support the four
 * standard constructor signatures: no-arg, message-only, message+cause, and
 * cause-only.
 *
 * <p>Subclasses implement the four factory methods and inherit the four
 * standard constructor tests; type-specific tests are added in the subclass.
 *
 * @param <T> the exception type under test
 * @author DbUnit.org
 */
public abstract class AbstractDbUnitExceptionTest<T extends Exception>
{
    protected abstract T createException();

    protected abstract T createException(String message);

    protected abstract T createException(String message, Throwable cause);

    protected abstract T createException(Throwable cause);

    @Test
    void testNoArgConstructor_withNoArgs_createsExceptionWithNullMessage()
    {
        final T actual = createException();
        assertThat(actual.getMessage()).as("message.").isNull();
        assertThat(actual.getCause()).as("cause.").isNull();
    }

    @Test
    void testMessageConstructor_withMessage_storesMessage()
    {
        final String message = "test error message";
        final T actual = createException(message);
        assertThat(actual.getMessage()).as("message.").isEqualTo(message);
        assertThat(actual.getCause()).as("cause.").isNull();
    }

    @Test
    void testMessageAndCauseConstructor_withMessageAndCause_storesBoth()
    {
        final String message = "error with cause";
        final Throwable cause = new RuntimeException("underlying problem");
        final T actual = createException(message, cause);
        assertThat(actual.getMessage()).as("message.").isEqualTo(message);
        assertThat(actual.getCause()).as("cause.").isSameAs(cause);
    }

    @Test
    void testCauseOnlyConstructor_withCause_wrapsThrowable()
    {
        final RuntimeException cause = new RuntimeException("root cause");
        final T actual = createException(cause);
        assertThat(actual.getCause()).as("cause.").isSameAs(cause);
        assertThat(actual.getMessage()).as("message includes cause class.")
                .contains("RuntimeException");
    }
}
