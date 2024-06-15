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
package org.dbunit.database.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
class ForeignKeyRelationshipEdgeTest
{
    private final ForeignKeyRelationshipEdge e1 =
            new ForeignKeyRelationshipEdge("table1", "table2", "fk_col",
                    "pk_col");
    private final ForeignKeyRelationshipEdge equal =
            new ForeignKeyRelationshipEdge("table1", "table2", "fk_col",
                    "pk_col");
    private final ForeignKeyRelationshipEdge notEqual1 =
            new ForeignKeyRelationshipEdge("table1", "tableOther", "fk_col",
                    "pk_col");
    private final ForeignKeyRelationshipEdge notEqual2 =
            new ForeignKeyRelationshipEdge("table1", "table2", "fk_col_other",
                    "pk_col");

    private final ForeignKeyRelationshipEdge equalSubclass =
            new ForeignKeyRelationshipEdge("table1", "table2", "fk_col",
                    "pk_col")
            {
            };

    @Test
    void testEqualsHashCode()
    {
        assertThat(e1).isEqualByComparingTo(equal)
                .isNotEqualByComparingTo(notEqual1)
                .isNotEqualByComparingTo(notEqual2)
                .isEqualByComparingTo(equalSubclass);
    }

    @Test
    void testCompareTo()
    {
        assertThat(e1.compareTo(equal))
                .as("Equal instances have different compareTo.").isZero();

        assertThat(e1.compareTo(notEqual1))
                .as("Unequal parent values with equal child values:"
                        + " first compared after second.")
                .isNegative();
        assertThat(notEqual1.compareTo(e1))
                .as("Unequal parent values with equal child values:"
                        + " first compared before second.")
                .isPositive();

        assertThat(e1.compareTo(notEqual2))
                .as("Equal parent values with unequal child values:"
                        + " first compared after second.")
                .isNegative();
        assertThat(notEqual2.compareTo(e1))
                .as("Equal parent values with unequal child values:"
                        + " first compared before second.")
                .isPositive();
    }
}
