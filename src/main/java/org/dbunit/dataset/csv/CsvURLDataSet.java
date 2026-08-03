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
package org.dbunit.dataset.csv;

import java.net.URL;

import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;

/**
 * This class constructs an IDataSet given a base URL containing CSV
 * files. It handles translations of "null" (the string), into null.
 * Based HEAVILY on {@link org.dbunit.dataset.csv.CsvDataSet} 
 *
 * @author Lenny Marks (lenny@aps.org)
 * @author Dion Gillard (diongillard@users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.1.0
 */
public class CsvURLDataSet extends CachedDataSet {

	/** base url that data can be found at */
//	private URL base;
	
	/**
	 * Create a Data Set from CSV files, using the base URL provided to find data.
	 *
	 * @param base the base URL that CSV files and the table ordering file are resolved against.
	 * @throws DataSetException if reading the CSV files fails.
	 */
	public CsvURLDataSet(URL base) throws DataSetException
	{
        super(new CsvURLProducer(base, CsvDataSet.TABLE_ORDERING_FILE));
//		this.base = base;
	}

}
