/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DbAssertionFailedError}.
 */
class DbAssertionFailedErrorTest
{
    @Test
    void testConstructor_noArgs_createsErrorWithNullMessage()
    {
        final DbAssertionFailedError error = new DbAssertionFailedError();

        assertThat(error).as("error instance.").isInstanceOf(Error.class);
        assertThat(error.getMessage()).as("message null.").isNull();
    }

    @Test
    void testConstructor_withMessage_createsErrorWithMessage()
    {
        final String message = "db assertion failed";

        final DbAssertionFailedError error = new DbAssertionFailedError(message);

        assertThat(error.getMessage()).as("message.").isEqualTo(message);
    }

    @Test
    void testIsError_always_isSubclassOfError()
    {
        final DbAssertionFailedError error = new DbAssertionFailedError("msg");

        assertThat(error).as("isError.").isInstanceOf(Error.class);
    }

    @Test
    void testConstructor_withEmptyMessage_createsErrorWithEmptyMessage()
    {
        final DbAssertionFailedError error = new DbAssertionFailedError("");

        assertThat(error.getMessage()).as("empty message.").isEmpty();
    }
}
