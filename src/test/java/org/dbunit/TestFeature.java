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
package org.dbunit;

/**
 * @author Manuel Laflamme
 * @since Apr 11, 2003
 * @version $Revision$
 */
public class TestFeature
{
    public static final TestFeature BLOB = new TestFeature("BLOB");
    public static final TestFeature CLOB = new TestFeature("CLOB");
    public static final TestFeature VARBINARY = new TestFeature("VARBINARY");
    public static final TestFeature TRANSACTION =
            new TestFeature("TRANSACTION");
    public static final TestFeature SCROLLABLE_RESULTSET =
            new TestFeature("SCROLLABLE_RESULTSET");
    public static final TestFeature INSERT_IDENTITY =
            new TestFeature("INSERT_IDENTITY");
    public static final TestFeature TRUNCATE_TABLE =
            new TestFeature("TRUNCATE_TABLE");
    public static final TestFeature SDO_GEOMETRY =
            new TestFeature("SDO_GEOMETRY");
    public static final TestFeature XML_TYPE = new TestFeature("XML_TYPE");

    private final String _name;

    private TestFeature(String name)
    {
        _name = name;
    }

    public String toString()
    {
        return _name;
    }
}
