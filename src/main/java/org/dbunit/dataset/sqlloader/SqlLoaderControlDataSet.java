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
package org.dbunit.dataset.sqlloader;

import java.io.File;
import java.util.List;

import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

/**
 * This class constructs an {@link IDataSet} given a directory containing control
 * files. It handles translations of "null"(the string), into null.
 * <p>
 * Example usage:
 * <code><pre>
 * File ctlDir = new File("src/sqlloader");
 * File orderedTablesFile = new File("src/sqlloader/tables.lst");
 * IDataSet dataSet = new SqlLoaderControlDataSet(ctlDir, orderedTablesFile);
 * </pre></code>
 * The file <code>orderedTablesFile</code> must contain the names of the tables to
 * be imported. As a convention the .ctl file must have the same name as the table names file.
 * Here an example of the &quot;tables.lst&quot; file:
 * <br>
 * <table border="1">
 * <tr><td>LANGUAGE<br>COUNTRY</td></tr>
 * </table>
 * The <code>ctlDir</code> directory must then contain the files <code>COUNTRY.ctl</code>
 * and <code>LANGUAGE.ctl</code>.
 * </p>
 * 
 * @author Stephan Strittmatter (stritti AT users.sourceforge.net), gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class SqlLoaderControlDataSet extends CachedDataSet implements IDataSet {

    /**
     * The Constructor.
     * 
     * @param ctlDir the control files directory
     * @param orderedTablesFile the table order file
     * 
     * @throws DataSetException the data set exception
     */
    public SqlLoaderControlDataSet(String ctlDir, String orderedTablesFile) 
    throws DataSetException 
    {
        super(new SqlLoaderControlProducer(ctlDir, orderedTablesFile));
    }

    /**
     * The Constructor.
     * 
     * @param ctlDir the control files directory
     * @param orderedTablesFile the table order file
     * 
     * @throws DataSetException the data set exception
     */
    public SqlLoaderControlDataSet(File ctlDir, File orderedTablesFile) 
    throws DataSetException 
    {
        super(new SqlLoaderControlProducer(ctlDir, orderedTablesFile));
    }
    
    /**
     * The Constructor.
     * 
     * @param ctlDir the control files directory
     * @param orderedTableNames a list of strings that contains the ordered table names
     * 
     * @throws DataSetException the data set exception
     */
    public SqlLoaderControlDataSet(File ctlDir, List orderedTableNames) 
    throws DataSetException 
    {
        super(new SqlLoaderControlProducer(ctlDir, orderedTableNames));
    }

}
