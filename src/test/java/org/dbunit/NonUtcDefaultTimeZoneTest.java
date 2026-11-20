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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;

/**
 * Fails when the unit test JVM runs in a time zone whose raw offset is 0, such as UTC.
 *
 * <p>
 * Code that adds or removes the JVM's own offset when it turns date and time text into instants,
 * such as {@code TimestampDataType} for text with an explicit offset, has a term that is zero in
 * such a zone, so a bug in it cannot show. The tests of that code, {@code TimestampDataTypeTest}
 * among them, still pass there but check nothing about it. Maven sets {@code Europe/Berlin} for the
 * unit tests through the {@code test.timezone} property. A run that skips Maven, such as one from
 * an IDE, needs the VM option {@code -Duser.timezone=Europe/Berlin} or another zone with an
 * offset.
 *
 * <p>
 * The raw offset leaves out daylight saving time, so London, Dublin and Lisbon fail this test even
 * in summer.
 *
 * @since 3.6.0
 */
class NonUtcDefaultTimeZoneTest
{
    @Test
    void testDefaultTimeZone_inTheUnitTestJvm_hasANonZeroRawOffset()
    {
        final TimeZone zone = TimeZone.getDefault();

        assertThat(zone.getRawOffset()).as(
                "The default time zone %s has a raw offset of 0, so tests of time zone arithmetic,"
                        + " such as TimestampDataTypeTest, pass but cannot show a bug in it. Maven"
                        + " sets Europe/Berlin through test.timezone; an IDE needs the VM option"
                        + " -Duser.timezone=Europe/Berlin.",
                zone.getID()).isNotZero();
    }
}
