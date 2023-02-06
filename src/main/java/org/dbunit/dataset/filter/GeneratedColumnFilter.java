/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2019, DbUnit.org
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

package org.dbunit.dataset.filter;

import org.dbunit.dataset.Column;

/**
 * Column filter that filters out generated columns.
 *
 * @author rcd (rcd AT users.sourceforge.net)
 * @since 3.0.0
 */
public class GeneratedColumnFilter implements IColumnFilter
{
    @Override
    public boolean accept(final String tableName, final Column column)
    {
        // allow column if it is not generated or we don't know
        final Boolean isGenerated = column.getGeneratedColumn();
        return isGenerated == null || !isGenerated;
    }
}
