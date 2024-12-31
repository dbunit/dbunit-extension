/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
package org.dbunit.dataset.datatype;

import java.util.Collection;

/**
 * Reports what database products this object relates to. Typically implemented
 * by an <code>IDaatTypeFactory</code>.
 *
 * @author John Hurst
 * @author Last changed by: $Author: $
 * @version $Revision:$ $Date:$
 * @since 2.4.6 (Septemeber 11, 2009)
 * @see IDataTypeFactory
 */
public interface IDbProductRelatable
{
    /**
     * Returns a set of Strings representing database product names that this
     * object relates to.
     * 
     * @return Set of Strings.
     */
    public Collection getValidDbProducts();
}
