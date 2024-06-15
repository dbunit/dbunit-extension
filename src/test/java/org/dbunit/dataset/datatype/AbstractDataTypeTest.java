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

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */

public abstract class AbstractDataTypeTest
{

    public abstract void testToString() throws Exception;

    public abstract void testGetTypeClass() throws Exception;

    public abstract void testIsNumber() throws Exception;

    public abstract void testIsDateTime() throws Exception;

    public abstract void testTypeCast() throws Exception;

    public abstract void testTypeCastNone() throws Exception;

    public abstract void testTypeCastInvalid() throws Exception;

    public abstract void testSqlType() throws Exception;

    public abstract void testForObject() throws Exception;

    public abstract void testAsString() throws Exception;

    public abstract void testCompareEquals() throws Exception;

    public abstract void testCompareDifferent() throws Exception;

    public abstract void testCompareInvalid() throws Exception;

    public abstract void testGetSqlValue() throws Exception;

}
