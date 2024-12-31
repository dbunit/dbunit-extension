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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * A datatype that is capable of storing UUIDs into BINARY fields (big-endian).
 * </p>
 * <p>
 * For the UUID to be detected as such, the string value of the field has to be
 * in the form of {@code uuid'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'}, where the
 * x's are the actual string value of the UUID, hex-encoded. Example:
 * </p>
 * 
 * <pre>
 *     &lt;company id="uuid'791ae85a-d8d0-11e2-8c43-50e549c9b654'" name="ACME"/&gt;
 * </pre>
 * 
 * @author Timur Strekalov
 */
public class UuidAwareBytesDataType extends BytesDataType
{
    /**
     * The regular expression for a hexadecimal UUID representation.
     */
    private static final Pattern UUID_RE = Pattern.compile(
            "uuid'([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})'");

    UuidAwareBytesDataType(final String name, final int sqlType)
    {
        super(name, sqlType);
    }

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        return super.typeCast(uuidAwareValueOf(value));
    }

    private static Object uuidAwareValueOf(final Object value)
    {
        if (value instanceof String)
        {
            final String s = (String) value;
            final Matcher m = UUID_RE.matcher(s);

            if (m.find())
            {
                final UUID uuid = UUID.fromString(m.group(1));
                return uuidToBytes(uuid);
            }
        }

        return value;
    }

    private static byte[] uuidToBytes(final UUID uuid)
    {
        final long msb = uuid.getMostSignificantBits();
        final long lsb = uuid.getLeastSignificantBits();

        return new byte[] {extractByte(msb, 0), extractByte(msb, 1),
                extractByte(msb, 2), extractByte(msb, 3), extractByte(msb, 4),
                extractByte(msb, 5), extractByte(msb, 6), extractByte(msb, 7),
                extractByte(lsb, 0), extractByte(lsb, 1), extractByte(lsb, 2),
                extractByte(lsb, 3), extractByte(lsb, 4), extractByte(lsb, 5),
                extractByte(lsb, 6), extractByte(lsb, 7)};
    }

    private static byte extractByte(final long value, final int byteIndex)
    {
        return (byte) (value >> (56 - byteIndex * 8) & 0xff);
    }
}
