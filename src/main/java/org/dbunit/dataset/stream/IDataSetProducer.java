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

import org.dbunit.dataset.DataSetException;


/**
 * Interface for reading a dataset using callback.
 *
 * @author Manuel Laflamme
 * @since Apr 17, 2003
 * @version $Revision$
 */
public interface IDataSetProducer
{
    /**
     * Sets the consumer notified of this producer's dataset content.
     *
     * @param consumer the consumer to notify.
     * @throws DataSetException if the consumer cannot be set.
     */
    public void setConsumer(IDataSetConsumer consumer) throws DataSetException;

    /**
     * Process this dataset source. During the processing, the IDataSetProducer
     * will provide information about the dataset through the specified event
     * listener.
     * <p>
     * This method is synchronous: it will not return until processing has ended.
     * If a client application wants to terminate parsing early, it should
     * throw an exception from the listener.
     *
     * @throws DataSetException if processing the dataset source fails.
     */
    public void produce() throws DataSetException;
}
