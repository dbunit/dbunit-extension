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
package org.dbunit.dataset.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileReader;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ForwardOnlyDataSetTest;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetTest;
import org.dbunit.dataset.xml.FlatXmlProducer;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.x (Apr 18, 2003)
 */
class StreamingDataSetTest extends ForwardOnlyDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final IDataSetProducer source = new FlatXmlProducer(new InputSource(
                new FileReader(FlatXmlDataSetTest.DATASET_FILE)));
        return new StreamingDataSet(source);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        return new StreamingDataSet(
                new DataSetProducerAdapter(super.createDuplicateDataSet()));
    }

    @Test
    void testReturnsOnException() throws Exception
    {
        final RuntimeException exceptionToThrow = new IllegalArgumentException(
                "For this test case we throw something that we normally would never do");
        final ExceptionThrowingProducer source =
                new ExceptionThrowingProducer(exceptionToThrow);
        final StreamingDataSet streamingDataSet = new StreamingDataSet(source);
        try
        {
            streamingDataSet.createIterator(false);
        } catch (final DataSetException expected)
        {
            final Throwable cause = expected.getCause();
            assertThat(cause.getClass())
                    .isEqualTo(IllegalArgumentException.class);
            assertThat(cause).isEqualTo(exceptionToThrow);
        }
    }

    private static class ExceptionThrowingProducer implements IDataSetProducer
    {
        private RuntimeException exceptionToThrow;

        public ExceptionThrowingProducer(
                final RuntimeException exceptionToThrow)
        {
            super();
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public void produce() throws DataSetException
        {
            throw exceptionToThrow;
        }

        @Override
        public void setConsumer(final IDataSetConsumer consumer)
                throws DataSetException
        {
            // Ignore for this test
        }

    }
}
