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
package org.dbunit.spring;

/**
 * The classpath locations of the data sets the {@code DbUnitTestExecutionListener} integration
 * tests share with the {@code DbUnitExtension} ones, instead of keeping copies of their own.
 */
final class ItDataSets
{
    /** One {@code TEST_TABLE} row, {@code row0} with {@code COLUMN1} set to {@code before}. */
    static final String PREP = "/org/dbunit/junit/jupiter/annotation-it-prep.xml";

    /** The same row with {@code COLUMN1} set to {@code after}. */
    static final String EXPECTED = "/org/dbunit/junit/jupiter/annotation-it-expected.xml";

    /** One {@code PK_TABLE} row. */
    static final String PK_PREP = "/org/dbunit/junit/jupiter/annotation-it-pk-prep.xml";

    private ItDataSets()
    {
    }
}
