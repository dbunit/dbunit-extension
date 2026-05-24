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

    public abstract void testToString_withDataType_returnsExpectedString() throws Exception;

    public abstract void testGetTypeClass_returnsExpectedClass() throws Exception;

    public abstract void testIsNumber_returnsExpectedBoolean() throws Exception;

    public abstract void testIsDateTime_returnsExpectedBoolean() throws Exception;

    public abstract void testTypeCast_withCompatibleInput_returnsExpectedValue() throws Exception;

    public abstract void testTypeCastNone_withNullInput_returnsNull() throws Exception;

    public abstract void testTypeCastInvalid_withIncompatibleInput_throwsTypeCastException() throws Exception;

    public abstract void testSqlType_returnsExpectedSqlType() throws Exception;

    public abstract void testForObject_withValidInput_returnsDataType() throws Exception;

    public abstract void testAsString_withValidInput_returnsStringRepresentation() throws Exception;

    public abstract void testCompareEquals_withEqualValues_returnsZero() throws Exception;

    public abstract void testCompareDifferent_withDifferentValues_returnsNonZero() throws Exception;

    public abstract void testCompareInvalid_withInvalidInput_throwsTypeCastException() throws Exception;

    public abstract void testGetSqlValue_withValidStatement_returnsExpectedValue() throws Exception;

}
