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

package org.dbunit.util.fileloader;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
class FullXmlDataFileLoaderTest
{
    FullXmlDataFileLoader loader = null;

    /**
     * {@inheritDoc}
     */
    @BeforeEach
    protected void setUp() throws Exception
    {
        loader = new FullXmlDataFileLoader();
    }

    /**
     * Test can load the specified file.
     */
    @Test
    void testLoad() throws DataSetException
    {
        final String filename = "/xml/xmlTableTest.xml";
        final IDataSet ds = loader.load(filename);
        assertThat(ds.getTableNames()).as("No tables found in dataset.")
                .hasSizeGreaterThan(0);
        // DataSet loading tests verify data accuracy
    }
}
