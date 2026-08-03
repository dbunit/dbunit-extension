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

import org.dbunit.dataset.AbstractTable;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.util.concurrent.BoundedBuffer;
import org.dbunit.util.concurrent.Channel;
import org.dbunit.util.concurrent.Puttable;
import org.dbunit.util.concurrent.Takable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous table iterator that uses a new Thread for asynchronous processing.
 * <p>
 * The producer runs on a daemon thread that communicates with this iterator through a
 * bounded channel. If this iterator is abandoned before the producer reaches the end of
 * the dataset, the producer thread stays parked waiting to put its next element onto the
 * channel until the JVM exits; there is no method to cancel it early.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Apr 17, 2003
 */
public class StreamingIterator implements ITableIterator
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(StreamingIterator.class);

    private static final Object EOD = new Object(); // end of dataset marker

    private final Takable _channel;
    private StreamingTable _activeTable;
    private Object _taken = null;
    private boolean _eod = false;
    /**
     * Variable to store an exception that might occur in the asynchronous consumer
     */
    private volatile Exception _asyncException;

	
    /**
     * Iterator that creates a table iterator by reading the input from
     * the given source in an asynchronous way. Therefore a Thread is
     * created.
     * @param source The source of the data
     * @throws DataSetException if the asynchronous producer thread is interrupted before
     *         producing its first element.
     */
    public StreamingIterator(IDataSetProducer source) throws DataSetException
    {
        Channel channel = new BoundedBuffer(30);
        _channel = channel;

        AsynchronousConsumer consumer = new AsynchronousConsumer(source, channel, this);
        Thread thread = new Thread(consumer, "StreamingIterator");
        thread.setDaemon(true);
        thread.start();

        // Take first element from asynchronous handler
        try
        {
            _taken = _channel.take();
        }
        catch (InterruptedException e)
        {
        	logger.debug("Thread '" + Thread.currentThread() + "' was interrupted");
        	throw resolveException(e);
        }
    }

    private DataSetException resolveException(InterruptedException cause) throws DataSetException 
    {
    	String msg = "Current thread was interrupted (Thread=" + Thread.currentThread() + ")";
    	if(this._asyncException != null)
    	{
            return new DataSetException(msg, this._asyncException);
    	}
    	else 
    	{
    		return new DataSetException(msg, cause);
    	}
	}

	////////////////////////////////////////////////////////////////////////////
    // ITableIterator interface

    public boolean next() throws DataSetException
    {
        logger.debug("next() - start");

        // End of dataset has previously been reach
        if (_eod)
        {
            return false;
        }

        // Iterate to the end of current table.
        while (_activeTable != null && _activeTable.next())
            ;

        // End of dataset is reach
        if (_taken == EOD)
        {
            _eod = true;
            _activeTable = null;

            logger.debug("End of iterator.");
            return false;
        }

        // New table
        if (_taken instanceof ITableMetaData)
        {
            _activeTable = new StreamingTable((ITableMetaData)_taken);
            return true;
        }

        throw new IllegalStateException(
                "Unexpected object taken from asyncronous handler: " + _taken);
    }

    public ITableMetaData getTableMetaData() throws DataSetException
    {
        logger.debug("getTableMetaData() - start");

        return _activeTable.getTableMetaData();
    }

    public ITable getTable() throws DataSetException
    {
        logger.debug("getTable() - start");

        return _activeTable;
    }

	private void handleException(Exception e)
	{
		// Is invoked when the asynchronous thread reports an exception
		this._asyncException = e;
	}

    ////////////////////////////////////////////////////////////////////////////
    // StreamingTable class

    private class StreamingTable extends AbstractTable
    {

        /**
         * Logger for this class
         */
        private final Logger logger = LoggerFactory.getLogger(StreamingTable.class);

        private ITableMetaData _metaData;
        private int _lastRow = -1;
        private boolean _eot = false;
        private Object[] _rowValues;

        public StreamingTable(ITableMetaData metaData)
        {
            _metaData = metaData;
        }

        boolean next() throws DataSetException
        {
            logger.debug("next() - start");

            // End of table has previously been reach
            if (_eot)
            {
                return false;
            }

            try
            {
                _taken = _channel.take();
                if (!(_taken instanceof Object[]))
                {
                    _eot = true;
                    return false;
                }

                _lastRow++;
                _rowValues = (Object[])_taken;
                return true;
            }
            catch (InterruptedException e)
            {
            	throw resolveException(e);
            }
        }

        ////////////////////////////////////////////////////////////////////////
        // ITable interface

        public ITableMetaData getTableMetaData()
        {
            logger.debug("getTableMetaData() - start");

            return _metaData;
        }

        public int getRowCount()
        {
            logger.debug("getRowCount() - start");

            throw new UnsupportedOperationException();
        }

        public Object getValue(int row, String columnName) throws DataSetException
        {
            if(logger.isDebugEnabled())
                logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), columnName);

            // Iterate up to specified row
            while (!_eot && row > _lastRow)
            {
                next();
            }

            if (row < _lastRow)
            {
                throw new UnsupportedOperationException("Cannot go backward!");
            }

            if (_eot || row > _lastRow)
            {
                throw new RowOutOfBoundsException(row + " > " + _lastRow);
            }

            return _rowValues[getColumnIndex(columnName)];
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName()).append("[");
            sb.append("_metaData=")
                    .append(this._metaData == null ? "null" : this._metaData
                            .toString());
            sb.append(", _eot=").append(this._eot);
            sb.append(", _lastRow=").append(this._lastRow);
            sb.append(", _rowValues=").append(
                    this._rowValues == null ? "null" : this._rowValues
                            .toString());
            sb.append("]");
            return sb.toString();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // AsynchronousConsumer class

    private static class AsynchronousConsumer implements Runnable, IDataSetConsumer
    {

        /**
         * Logger for this class
         */
        private static final Logger logger = LoggerFactory.getLogger(AsynchronousConsumer.class);

        private final IDataSetProducer _producer;
        private final Puttable _channel;
        private final StreamingIterator _exceptionHandler;
        private final Thread _invokerThread;

        public AsynchronousConsumer(IDataSetProducer source, Puttable channel, StreamingIterator exceptionHandler)
        {
            _producer = source;
            _channel = channel;
            _exceptionHandler = exceptionHandler;
            _invokerThread = Thread.currentThread();
        }

        ////////////////////////////////////////////////////////////////////////
        // Runnable interface

        public void run()
        {
            logger.debug("run() - start");

            try
            {
                _producer.setConsumer(this);
                _producer.produce();
            }
            catch (Exception e)
            {
            	_exceptionHandler.handleException(e);
            	// Since the invoker thread probably waits tell it that we have finished here
            	_invokerThread.interrupt();
            }
            
            logger.debug("End of thread " + Thread.currentThread());
        }

        ////////////////////////////////////////////////////////////////////////
        // IDataSetConsumer interface

        public void startDataSet() throws DataSetException
        {
        }

        public void endDataSet() throws DataSetException
        {
            logger.debug("endDataSet() - start");

            try
            {
                _channel.put(EOD);
            }
            catch (InterruptedException e)
            {
                throw new DataSetException("Operation was interrupted");
            }
        }

        public void startTable(ITableMetaData metaData) throws DataSetException
        {
            logger.debug("startTable(metaData={}) - start", metaData);

            try
            {
                _channel.put(metaData);
            }
            catch (InterruptedException e)
            {
                throw new DataSetException("Operation was interrupted");
            }
        }

        public void endTable() throws DataSetException
        {
        }

        public void row(Object[] values) throws DataSetException
        {
            logger.debug("row(values={}) - start", values);

            try
            {
                _channel.put(values);
            }
            catch (InterruptedException e)
            {
                throw new DataSetException("Operation was interrupted");
            }
        }
    }

}
