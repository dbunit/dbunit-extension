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

package org.dbunit.dataset.datatype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.ITable;

import java.sql.Types;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class UnknownDataType extends AbstractDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(UnknownDataType.class);

    UnknownDataType()
    {
        super("UNKNOWN", Types.OTHER, Object.class, false);
    }

    ////////////////////////////////////////////////////////////////////////////
    // DataType class

    public Object typeCast(Object value) throws TypeCastException
    {
        logger.debug("typeCast(value={}) - start", value);

        if (value == ITable.NO_VALUE)
        {
            return null;
        }

        return value;
    }

    public int compare(Object o1, Object o2) throws TypeCastException
    {
        logger.debug("compare(o1={}, o2={}) - start", o1, o2);

        // New since dbunit 2.4 for performance optimization (avoid the
        // "asString")
        if (areObjectsEqual(o1, o2))
        {
            return 0;
        }

        return super.compare(asString(o1), asString(o2));
    }
}
