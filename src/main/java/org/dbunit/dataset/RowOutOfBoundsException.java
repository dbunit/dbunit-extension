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

package org.dbunit.dataset;


/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */

public class RowOutOfBoundsException extends DataSetException
{
    /** Matches the default (compiler-computed) value from dbunit 3.2.0, the last release
     *  before {@link #fillInStackTrace()} was added; pinning it keeps this class
     *  serialization-compatible with 3.2.0 and stable across all releases from here on. */
    private static final long serialVersionUID = 3366609800061836144L;

    public RowOutOfBoundsException()
    {
    }

    public RowOutOfBoundsException(String msg)
    {
        super(msg);
    }

    public RowOutOfBoundsException(String msg, Throwable e)
    {
        super(msg, e);
    }

    public RowOutOfBoundsException(Throwable e)
    {
        super(e);
    }

    /**
     * Suppresses stack trace capture.
     * This exception is thrown once per table scan as normal end-of-rows control flow,
     * not as an error condition, so capturing a stack trace on every throw is wasted work.
     * @return This exception, without a captured stack trace.
     */
    @Override
    public synchronized Throwable fillInStackTrace()
    {
        return this;
    }
}





