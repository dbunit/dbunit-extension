/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2008, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import org.dbunit.dataset.ITable;

/**
 * Handles the failure of an assertion.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public interface FailureHandler extends DifferenceListener, FailureFactory
{

    /**
     * Returns a string to be appended to the assertion failure message. Is used to 
     * provide some more information about a failure (for example to print out some
     * PK columns for identifying the failed rows in the DB).
     * @param expectedTable
     * @param actualTable
     * @param row The row for which the assertion failed
     * @param columnName The column for which the assertion failed
     * @return A string that is appended to the assertion failure message
     */
    public String getAdditionalInfo(ITable expectedTable, ITable actualTable,
            int row, String columnName);


}
