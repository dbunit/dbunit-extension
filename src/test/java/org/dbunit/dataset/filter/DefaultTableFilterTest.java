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
package org.dbunit.dataset.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultTableFilter}.
 */
class DefaultTableFilterTest
{
    // -------------------------------------------------------------------------
    // Empty filter — no includes, no excludes: accept everything
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withEmptyFilter_acceptsAnyName() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();

        assertThat(filter.isValidName("ORDERS")).as("empty filter accepts ORDERS.").isTrue();
        assertThat(filter.isValidName("CUSTOMERS")).as("empty filter accepts CUSTOMERS.")
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Include-only
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withInclude_acceptsMatchingTable() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDERS");

        assertThat(filter.isValidName("ORDERS")).as("included table accepted.").isTrue();
    }

    @Test
    void testIsValidName_withInclude_rejectsNonIncludedTable() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDERS");

        assertThat(filter.isValidName("CUSTOMERS")).as("non-included table rejected.")
                .isFalse();
    }

    @Test
    void testIsValidName_withIncludeWildcard_acceptsMatchingTables() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDER*");

        assertThat(filter.isValidName("ORDERS")).as("wildcard matches ORDERS.").isTrue();
        assertThat(filter.isValidName("ORDER_ITEMS")).as("wildcard matches ORDER_ITEMS.")
                .isTrue();
    }

    @Test
    void testIsValidName_withIncludeWildcard_rejectsNonMatchingTable() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDER*");

        assertThat(filter.isValidName("CUSTOMERS")).as("wildcard rejects CUSTOMERS.")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Exclude-only
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withExclude_rejectsMatchingTable() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.excludeTable("AUDIT_LOG");

        assertThat(filter.isValidName("AUDIT_LOG")).as("excluded table rejected.").isFalse();
    }

    @Test
    void testIsValidName_withExclude_acceptsNonExcludedTable() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.excludeTable("AUDIT_LOG");

        assertThat(filter.isValidName("ORDERS")).as("non-excluded table accepted.").isTrue();
    }

    @Test
    void testIsValidName_withExcludeWildcard_rejectsMatchingTables() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.excludeTable("AUDIT*");

        assertThat(filter.isValidName("AUDIT_LOG")).as("wildcard rejects AUDIT_LOG.")
                .isFalse();
        assertThat(filter.isValidName("AUDIT_TRAIL")).as("wildcard rejects AUDIT_TRAIL.")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Combined include + exclude
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withIncludeAndExclude_acceptsIncludedButNotExcluded()
            throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDER*");
        filter.excludeTable("ORDER_ARCHIVE");

        assertThat(filter.isValidName("ORDERS")).as("included and not excluded: accepted.")
                .isTrue();
        assertThat(filter.isValidName("ORDER_ARCHIVE"))
                .as("included but also excluded: rejected.").isFalse();
    }

    @Test
    void testIsValidName_withIncludeAndExclude_rejectsNonIncluded() throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDERS");
        filter.excludeTable("AUDIT_LOG");

        assertThat(filter.isValidName("CUSTOMERS"))
                .as("not included, not excluded: rejected because include filter is active.")
                .isFalse();
    }

    @Test
    void testIsValidName_withIncludeAndExclude_tableInBothIncludeAndExclude_rejected()
            throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDERS");
        filter.excludeTable("ORDERS");

        assertThat(filter.isValidName("ORDERS"))
                .as("table in both include and exclude: exclude wins.").isFalse();
    }

    // -------------------------------------------------------------------------
    // Case insensitivity (delegated to IncludeTableFilter / ExcludeTableFilter)
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withLowercaseTableName_acceptedCaseInsensitively()
            throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDERS");

        assertThat(filter.isValidName("orders")).as("case-insensitive include match.")
                .isTrue();
    }

    @Test
    void testIsValidName_withLowercaseExclude_rejectsUppercaseTableName()
            throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.excludeTable("audit_log");

        assertThat(filter.isValidName("AUDIT_LOG")).as("case-insensitive exclude match.")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Multiple includes / excludes
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withMultipleIncludes_acceptsAllIncludedTables()
            throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.includeTable("ORDERS");
        filter.includeTable("CUSTOMERS");

        assertThat(filter.isValidName("ORDERS")).as("first include accepted.").isTrue();
        assertThat(filter.isValidName("CUSTOMERS")).as("second include accepted.").isTrue();
        assertThat(filter.isValidName("PRODUCTS")).as("non-included rejected.").isFalse();
    }

    @Test
    void testIsValidName_withMultipleExcludes_rejectsAllExcludedTables()
            throws DataSetException
    {
        final DefaultTableFilter filter = new DefaultTableFilter();
        filter.excludeTable("AUDIT_LOG");
        filter.excludeTable("SESSION_LOG");

        assertThat(filter.isValidName("AUDIT_LOG")).as("first exclude rejected.").isFalse();
        assertThat(filter.isValidName("SESSION_LOG")).as("second exclude rejected.").isFalse();
        assertThat(filter.isValidName("ORDERS")).as("non-excluded accepted.").isTrue();
    }
}
