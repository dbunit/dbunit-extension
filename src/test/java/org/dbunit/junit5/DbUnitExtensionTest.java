/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2025, DbUnit.org
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
package org.dbunit.junit5;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.dbunit.IDatabaseTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbUnitExtensionTest {
    @Mock
    ExtensionContext context;

    @Mock
    ExtensionContext.Store store;

    @Mock
    IDatabaseTester databaseTester;

    final DbUnitExtension extension = new DbUnitExtension();

    // ---- beforeTestExecution: field discovery happy paths ----

    @Test
    void testBeforeTestExecution_testerFieldPresent_callsOnSetupAndStoresTester() throws Exception {
        final HasTester testInstance = new HasTester(databaseTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));

        extension.beforeTestExecution(context);

        verify(store).put(eq("databaseTester"), eq(databaseTester));
        verify(databaseTester).onSetup();
    }

    @Test
    void testBeforeTestExecution_privateField_callsOnSetup() throws Exception {
        final HasPrivateTester testInstance = new HasPrivateTester(databaseTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));

        extension.beforeTestExecution(context);

        verify(databaseTester).onSetup();
    }

    @Test
    void testBeforeTestExecution_testerInSuperclass_callsOnSetup() throws Exception {
        final SubclassOfHasTester testInstance = new SubclassOfHasTester(databaseTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));

        extension.beforeTestExecution(context);

        verify(databaseTester).onSetup();
    }

    @Test
    void testBeforeTestExecution_testerInGrandparentClass_callsOnSetup() throws Exception {
        final SubSubclassOfHasTester testInstance = new SubSubclassOfHasTester(databaseTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));

        extension.beforeTestExecution(context);

        verify(databaseTester).onSetup();
    }

    @Test
    void testBeforeTestExecution_subclassFieldTakesPrecedenceOverSuperclassField_usesSubclassField()
            throws Exception {
        final IDatabaseTester superTester = mock(IDatabaseTester.class);
        final SubclassWithOwnTester testInstance = new SubclassWithOwnTester(databaseTester, superTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));

        extension.beforeTestExecution(context);

        verify(databaseTester).onSetup();
        verify(superTester, never()).onSetup();
    }

    // ---- beforeTestExecution: error cases ----

    @Test
    void testBeforeTestExecution_noTestInstance_throwsIllegalStateException() {
        when(context.getTestInstance()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> extension.beforeTestExecution(context))
                .as("Expected exception when no test instance is available.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No test instance available");
    }

    @Test
    void testBeforeTestExecution_noTesterField_throwsIllegalStateException() {
        when(context.getTestInstance()).thenReturn(Optional.of(new Object()));

        assertThatThrownBy(() -> extension.beforeTestExecution(context))
                .as("Expected exception when no IDatabaseTester field is present.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field found");
    }

    @Test
    void testBeforeTestExecution_nullTesterField_throwsIllegalStateException() {
        final HasTester testInstance = new HasTester(null);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));

        assertThatThrownBy(() -> extension.beforeTestExecution(context))
                .as("Expected exception when IDatabaseTester field is null.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is null");
    }

    @Test
    void testBeforeTestExecution_staticTesterField_throwsIllegalStateException() {
        when(context.getTestInstance()).thenReturn(Optional.of(new HasStaticTesterOnly()));

        assertThatThrownBy(() -> extension.beforeTestExecution(context))
                .as("Static IDatabaseTester fields must be skipped.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field found");
    }

    @Test
    void testBeforeTestExecution_onSetupThrows_propagatesException() throws Exception {
        final HasTester testInstance = new HasTester(databaseTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));
        final Exception cause = new Exception("database setup failed");
        doThrow(cause).when(databaseTester).onSetup();

        assertThatThrownBy(() -> extension.beforeTestExecution(context))
                .as("Exception from onSetup() must propagate.")
                .isSameAs(cause);
    }

    // ---- afterTestExecution ----

    @Test
    void testAfterTestExecution_storedTesterPresent_callsOnTearDown() throws Exception {
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(store.get("databaseTester", IDatabaseTester.class)).thenReturn(databaseTester);

        extension.afterTestExecution(context);

        verify(databaseTester).onTearDown();
    }

    @Test
    void testAfterTestExecution_noStoredTester_doesNotCallOnTearDown() throws Exception {
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(store.get("databaseTester", IDatabaseTester.class)).thenReturn(null);

        extension.afterTestExecution(context);

        verify(databaseTester, never()).onTearDown();
    }

    @Test
    void testAfterTestExecution_onTearDownThrows_propagatesException() throws Exception {
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(store.get("databaseTester", IDatabaseTester.class)).thenReturn(databaseTester);
        final Exception cause = new Exception("database teardown failed");
        doThrow(cause).when(databaseTester).onTearDown();

        assertThatThrownBy(() -> extension.afterTestExecution(context))
                .as("Exception from onTearDown() must propagate.")
                .isSameAs(cause);
    }

    // ---- helper inner classes ----

    static class HasTester {
        IDatabaseTester databaseTester;

        HasTester(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class HasPrivateTester {
        private final IDatabaseTester databaseTester;

        HasPrivateTester(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class HasStaticTesterOnly {
        @SuppressWarnings("unused")
        static IDatabaseTester staticTester = null;
    }

    static class ParentHasTester {
        IDatabaseTester databaseTester;

        ParentHasTester(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class SubclassOfHasTester extends ParentHasTester {
        SubclassOfHasTester(final IDatabaseTester databaseTester) {
            super(databaseTester);
        }
    }

    static class SubSubclassOfHasTester extends SubclassOfHasTester {
        SubSubclassOfHasTester(final IDatabaseTester databaseTester) {
            super(databaseTester);
        }
    }

    static class SuperclassWithTester {
        IDatabaseTester databaseTester;

        SuperclassWithTester(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class SubclassWithOwnTester extends SuperclassWithTester {
        IDatabaseTester databaseTester;

        SubclassWithOwnTester(final IDatabaseTester subTester, final IDatabaseTester superTester) {
            super(superTester);
            this.databaseTester = subTester;
        }
    }
}
