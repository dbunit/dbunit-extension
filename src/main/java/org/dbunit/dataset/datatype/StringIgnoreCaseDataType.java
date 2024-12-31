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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * String data type that ignore case when comparing String values.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class StringIgnoreCaseDataType extends StringDataType
{
    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(StringIgnoreCaseDataType.class);

    public StringIgnoreCaseDataType(String name, int sqlType)
    {
        super(name, sqlType);
    }

    protected int compareNonNulls(Object value1, Object value2)
            throws TypeCastException
    {
        logger.debug("compareNonNulls(value1={}, value2={}) - start", value1,
                value2);

        String value1cast = (String) value1;
        String value2cast = (String) value2;
        return value1cast.compareToIgnoreCase(value2cast);
    }

}
