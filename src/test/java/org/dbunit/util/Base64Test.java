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
package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class Base64Test
{
    @Test
    void testDecode_withInvalidCharacter_logsWarningAndReturnsNull()
    {
        final Logger base64Logger =
                (Logger) LoggerFactory.getLogger(Base64.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        base64Logger.addAppender(appender);
        try
        {
            final byte[] decoded = Base64.decode("!!!!");

            assertThat(decoded)
                    .as("decode() must keep returning null on invalid input instead of throwing.")
                    .isNull();
            assertThat(appender.list)
                    .as("The bad-input diagnostic must be logged at WARN instead of printed to stderr.")
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .hasSize(1);
        } finally
        {
            base64Logger.detachAppender(appender);
        }
    }
}
