/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

class DbUnitSpringTestTest
{
    @Test
    void testDbUnitSpringTest_onTestClass_registersTheListenerMergedWithSpringsDefaults()
    {
        final TestExecutionListeners registration =
                TestContextAnnotationUtils.findMergedAnnotation(Sample.class,
                        TestExecutionListeners.class);

        assertThat(registration)
                .as("Spring must see @DbUnitSpringTest as a @TestExecutionListeners declaration.")
                .isNotNull();
        assertThat(registration.listeners())
                .as("The listener it registers must be DbUnitTestExecutionListener.")
                .containsExactly(DbUnitTestExecutionListener.class);
        assertThat(registration.mergeMode())
                .as("The listener must be merged with Spring's default listeners: the"
                        + " annotation's own default, REPLACE_DEFAULTS, would silently drop"
                        + " dependency injection and transaction management.")
                .isEqualTo(MergeMode.MERGE_WITH_DEFAULTS);
    }

    @DbUnitSpringTest
    static class Sample
    {
    }
}
