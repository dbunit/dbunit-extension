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

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link FailureHandler} that collects the {@link Difference}s that
 * were found without throwing an exception.
 * <p>
 * You can use it as follows:
 * <code><pre>
 * IDataSet dataSet = getDataSet();
 * DiffCollectingFailureHandler myHandler = new DiffCollectingFailureHandler();
 * //invoke the assertion with the custom handler
 * assertion.assertEquals(dataSet.getTable("TEST_TABLE"),
 *                        dataSet.getTable("TEST_TABLE_WITH_WRONG_VALUE"),
 *                        myHandler);
 * // Evaluate the results
 * List diffList = myHandler.getDiffList();
 * Difference diff = (Difference)diffList.get(0);
 * ...
 * </pre></code>
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class DiffCollectingFailureHandler extends DefaultFailureHandler 
{
    private final List diffList = new ArrayList();
    
    public void handle(Difference diff) 
    {
        // Simply collect the difference without throwing an exception
        this.diffList.add(diff);
    }

    /**
     * @return The list of collected {@link Difference}s
     */
    public List getDiffList() 
    {
        return diffList;
    }

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(DiffCollectingFailureHandler.class.getName()).append("[");
        sb.append("diffList=").append(diffList);
        sb.append("]");
        return sb.toString();
    }
}
