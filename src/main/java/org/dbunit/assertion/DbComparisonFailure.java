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

/**
 * Exception signaling a DbUnit assertion failure while comparing values.
 * Is used to avoid the direct dependency to any other testing framework.
 *
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class DbComparisonFailure extends AssertionError
{
    private static final long serialVersionUID = 1L;

    private String reason;
    private String expected;
    private String actual;

    /**
     * @param reason The reason for the comparison failure
     * @param expected The expected value
     * @param actual The actual value
     */
    public DbComparisonFailure(final String reason, final String expected, final String actual)
    {
        super(reason);
        this.reason = reason;
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String getMessage()
    {
        return buildMessage(this.reason, this.expected, this.actual);
    }

    public String getReason()
    {
        return reason;
    }

    public String getExpected()
    {
        return expected;
    }

    public String getActual()
    {
        return actual;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append(getClass().getName()).append("[");
        sb.append(reason);
        sb.append("expected:<").append(expected);
        sb.append("> but was:<").append(actual).append(">");
        sb.append("]");
        return sb.toString();
    }


    /**
     * Creates a formatted message string from the given parameters
     * @param reason The reason for an assertion or comparison failure
     * @param expected The expected result
     * @param actual The actual result
     * @return The formatted message
     */
    public static final String buildMessage(final String reason, final String expected, final String actual)
    {
        final StringBuffer sb = new StringBuffer();
        sb.append(reason);
        sb.append(" expected:<").append(expected).append(">");
        sb.append(" but was:<").append(actual).append(">");
        return sb.toString();

    }
}
