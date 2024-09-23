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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Decorator that replaces configured values from the decorated table
 * with replacement values.
 *
 * @author Manuel Laflamme
 * @since Mar 17, 2003
 * @version $Revision$
 */
public class ReplacementTable implements ITable
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ReplacementTable.class);

    private final ITable _table;
    private final Map _objectMap;
    private final Map _substringMap;
    private String _startDelim;
    private String _endDelim;
    private boolean _strictReplacement;

    /**
     * Create a new ReplacementTable object that decorates the specified table.
     *
     * @param table the decorated table
     */
    public ReplacementTable(ITable table)
    {
        this(table, new HashMap(), new HashMap(), null, null);
    }

    public ReplacementTable(ITable table, Map objectMap, Map substringMap,
            String startDelimiter, String endDelimiter)
    {
        _table = table;
        _objectMap = objectMap;
        _substringMap = substringMap;
        _startDelim = startDelimiter;
        _endDelim = endDelimiter;
    }

    /**
     * Setting this property to true indicates that when no replacement
     * is found for a delimited substring the replacement will fail fast.
     * 
     * @param strictReplacement true if replacement should be strict
     */
    public void setStrictReplacement(boolean strictReplacement)
    {
    	if(logger.isDebugEnabled())
    		logger.debug("setStrictReplacement(strictReplacement={}) - start", strictReplacement);
    	
        this._strictReplacement = strictReplacement;
    }
    
    /**
     * Add a new Object replacement mapping.
     *
     * @param originalObject the object to replace
     * @param replacementObject the replacement object
     */
    public void addReplacementObject(Object originalObject, Object replacementObject)
    {
        logger.debug("addReplacementObject(originalObject={}, replacementObject={}) - start", originalObject, replacementObject);

        _objectMap.put(originalObject, replacementObject);
    }

    /**
     * Add a new substring replacement mapping.
     *
     * @param originalSubstring the substring to replace
     * @param replacementSubstring the replacement substring
     */
    public void addReplacementSubstring(String originalSubstring,
            String replacementSubstring)
    {
        logger.debug("addReplacementSubstring(originalSubstring={}, replacementSubstring={}) - start", originalSubstring, replacementSubstring);

        if (originalSubstring == null || replacementSubstring == null)
        {
            throw new NullPointerException();
        }

        _substringMap.put(originalSubstring, replacementSubstring);
    }

    /**
     * Sets substring delimiters.
     */
    public void setSubstringDelimiters(String startDelimiter, String endDelimiter)
    {
        logger.debug("setSubstringDelimiters(startDelimiter={}, endDelimiter={}) - start", startDelimiter, endDelimiter);

        if (startDelimiter == null || endDelimiter == null)
        {
            throw new NullPointerException();
        }

        _startDelim = startDelimiter;
        _endDelim = endDelimiter;
    }

    /**
     * Replace occurrences of source in text with target. Operates directly on text.
     */
    private void replaceAll(StringBuilder text, String source, String target) {
        int index = 0;
        while((index = text.toString().indexOf(source, index)) != -1)
        {
            text.replace(index, index+source.length(), target);
            index += target.length();
        }
    }
    
    private String replaceStrings(String value, String lDelim, String rDelim) {
        final StringBuilder buffer = new StringBuilder(value);

        for (Iterator it = _substringMap.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry)it.next();
            String original = (String)entry.getKey();
            String replacement = (String)entry.getValue();
            replaceAll(buffer, lDelim + original + rDelim, replacement);
        }

        return buffer == null ? value : buffer.toString();        
    }
    
    private String replaceSubstrings(String value)
    {
        return replaceStrings(value, "", "");
    }    

    /**
     * @throws DataSetException when stringReplacement fails
     */
    private String replaceDelimitedSubstrings(String value) throws DataSetException
    {
        StringBuilder buffer = null;

        int startIndex = 0;
        int endIndex = 0;
        int lastEndIndex = 0;
        for(;;)
        {
            startIndex = value.indexOf(_startDelim, lastEndIndex);
            if (startIndex != -1)
            {
                endIndex = value.indexOf(_endDelim, startIndex + _startDelim.length());
                if (endIndex != -1)
                {
                    if (buffer == null)
                    {
                        buffer = new StringBuilder();
                    }

                    String substring = value.substring(
                            startIndex + _startDelim.length(), endIndex);
                    if (_substringMap.containsKey(substring))
                    {
                        buffer.append(value.substring(lastEndIndex, startIndex));
                        buffer.append(_substringMap.get(substring));
                    }
                    else if (_strictReplacement)
                    {
                        throw new DataSetException(
                                "Strict Replacement was set to true, but no"
                                + " replacement was found for substring '" 
                                + substring + "' in the value '" + value + "'");
                    }
                    else
                    {
                        logger.debug("Did not find a replacement map entry for substring={}. " +
                        		"Leaving original value there.", substring);
                        buffer.append(value.substring(
                                lastEndIndex, endIndex + _endDelim.length()));
                    }

                    lastEndIndex = endIndex + _endDelim.length();
                }
            }

            // No more delimited substring
            if (startIndex == -1 || endIndex == -1)
            {
                if (buffer != null)
                {
                    buffer.append(value.substring(lastEndIndex));
                }
                break;
            }
        }

        return buffer == null ? value : buffer.toString();
    }

    ////////////////////////////////////////////////////////////////////////
    // ITable interface

    public ITableMetaData getTableMetaData()
    {
        return _table.getTableMetaData();
    }

    public int getRowCount()
    {
        return _table.getRowCount();
    }

    public Object getValue(int row, String column) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, columnName={}) - start", row, column);

        Object value = _table.getValue(row, column);

        // Object replacement
        if (_objectMap.containsKey(value))
        {
            return _objectMap.get(value);
        }

        // Stop here if substring replacement not applicable
        if (_substringMap.size() == 0 || !(value instanceof String))
        {
            return value;
        }

        // Substring replacement
        if (_startDelim != null && _endDelim != null)
        {
            return replaceDelimitedSubstrings((String)value);
        }
        return replaceSubstrings((String)value);
    }
    
    
    public String toString()
    {
    	final StringBuilder sb = new StringBuilder();
    	sb.append(getClass().getName()).append("[");
    	sb.append("_strictReplacement=").append(_strictReplacement);
    	sb.append(", _table=").append(_table);
    	sb.append(", _objectMap=").append(_objectMap);
    	sb.append(", _substringMap=").append(_substringMap);
    	sb.append(", _startDelim=").append(_startDelim);
    	sb.append(", _endDelim=").append(_endDelim);
    	sb.append("]");
    	return sb.toString();
    }
}
