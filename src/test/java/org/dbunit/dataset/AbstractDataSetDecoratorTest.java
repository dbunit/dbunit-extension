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
 * Abstract class for testing {@link IDataSet} implementations which only
 * decorate another {@link IDataSet} implementation. The check for duplicate
 * table names can be omitted in this case since no new tables are created by
 * normal decorators.
 *
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public abstract class AbstractDataSetDecoratorTest extends AbstractDataSetTest
{

    @Override
    protected final IDataSet createDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final IDataSet createMultipleCaseDuplicateDataSet()
            throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void testCreateDuplicateDataSet() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

    @Override
    public final void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

}
