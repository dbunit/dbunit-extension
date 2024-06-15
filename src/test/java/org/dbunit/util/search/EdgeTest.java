/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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
package org.dbunit.util.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
class EdgeTest
{

    @Test
    void testEqualsHashCode()
    {
        final Edge e1 = new Edge("table1", "table2");
        final Edge e2 = new Edge("table1", "table2");
        final Edge eNotEqual = new Edge("table1", "tableOther");
        final Edge eEqualSubclass = new Edge("table1", "table2")
        {
        };

        assertThat(e1).as("e1 is equal to e2").hasSameHashCodeAs(e2)
                .as("e1 is equal to eNotEqual")
                .doesNotHaveSameHashCodeAs(eNotEqual)
                .as("e1 is equal to eEqualSubclass")
                .hasSameHashCodeAs(eEqualSubclass);
    }

    @Test
    void testCompare()
    {
        final Edge e1 = new Edge("table1", "table2");
        final Edge e2 = new Edge("table1", "table2");
        final Edge eNotEqual = new Edge("table1", "tableOther");

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.compareTo(eNotEqual)).isEqualTo(-29);
    }
}
