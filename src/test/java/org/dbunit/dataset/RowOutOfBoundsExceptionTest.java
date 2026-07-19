/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author Jeff Jensen
 * @since 3.2.1
 */
class RowOutOfBoundsExceptionTest
{
    @Test
    void testFillInStackTrace_newInstance_hasEmptyStackTrace() throws Exception
    {
        final RowOutOfBoundsException exception = new RowOutOfBoundsException();

        assertThat(exception.getStackTrace())
                .as("End-of-rows control-flow exception should not capture a stack trace.")
                .isEmpty();
    }

    @Test
    void testGetMessage_constructedWithMessage_preservesMessage() throws Exception
    {
        final RowOutOfBoundsException exception = new RowOutOfBoundsException("row 42 is out of bounds");

        assertThat(exception.getMessage())
                .as("Message passed to the constructor should still be available via getMessage().")
                .isEqualTo("row 42 is out of bounds");
    }

}
