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
package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Nov 5, 2005
 *
 */
public class CollectionsHelperTest
{

    public static final String A = "A";
    public static final String B = "B";
    public static final String C = "C";

    @Test
    void testObjectsToSetNullEntry()
    {
        final Set<Object> output = CollectionsHelper.objectsToSet(null);
        assertThat(output).as("set should be null").isNull();
    }

    @Test
    void testObjectsToSetEmptyEntry()
    {
        final Set<Object> output =
                CollectionsHelper.objectsToSet(new Object[0]);
        assertThat(output).as("set should not be null").isNotNull()
                .as("set should be empty").isEmpty();
    }

    @Test
    void testObjectsToSetSingleInput()
    {
        final Object[] input = {A};
        final Set<Object> output = CollectionsHelper.objectsToSet(input);
        assertThat(output).as("set should not be null").isNotNull();
        final Iterator<Object> i = output.iterator();
        assertThat(i).as("iterator is empty").hasNext();
        assertThat(i.next()).as("element 0 match").isEqualTo(A);
        assertThat(i.hasNext()).as("iterator is not empty").isFalse();
    }

    @Test
    void testObjectsToSetSequence()
    {
        final Object[] input = {A, C, B};
        final Set<Object> output = CollectionsHelper.objectsToSet(input);
        assertThat(output).as("set should not be null").isNotNull();
        final Iterator<Object> i = output.iterator();
        assertThat(i).as("iterator is empty").hasNext();
        assertThat(i.next()).as("element 0 match").isEqualTo(A);
        assertThat(i.next()).as("element 1 match").isEqualTo(C);
        assertThat(i.next()).as("element 2 match").isEqualTo(B);
        assertThat(i.hasNext()).as("iterator is not empty").isFalse();
    }

    @Test
    void testSetToObjectsNullEntry()
    {
        final Object[] output = CollectionsHelper.setToObjects(null);
        assertThat(output).as("array should be null").isNull();
    }

    @Test
    void testSetToObjectsEmptyEntry()
    {
        final Set<Object> input = new HashSet<>();
        final Object[] output = CollectionsHelper.setToObjects(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array should be empty").isEmpty();
    }

    @Test
    void testSetToObjectsSingle()
    {
        final Set<Object> input = new HashSet<>();
        input.add(A);
        final Object[] output = CollectionsHelper.setToObjects(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array size does not match").hasSize(1);
        assertThat(output[0]).as("element 0 does not match").isEqualTo(A);
    }

    @Test
    void testSetToObjectsOrderedSet()
    {
        final Set<Object> input = new TreeSet<>();
        input.add(A);
        input.add(C);
        input.add(B);
        final Object[] output = CollectionsHelper.setToObjects(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array size does not match").hasSize(3);
        assertThat(output[0]).as("element 0 does not match").isEqualTo(A);
        assertThat(output[1]).as("element 1 does not match").isEqualTo(B);
        assertThat(output[2]).as("element 2 does not match").isEqualTo(C);
    }

    @Test
    void testSetToObjectsSequencialSet()
    {
        final Set<Object> input = new LinkedHashSet<>();
        input.add(A);
        input.add(C);
        input.add(B);
        final Object[] output = CollectionsHelper.setToObjects(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array size does not match").hasSize(3);
        assertThat(output[0]).as("element 0 does not match").isEqualTo(A);
        assertThat(output[1]).as("element 1 does not match").isEqualTo(C);
        assertThat(output[2]).as("element 2 does not match").isEqualTo(B);
    }

    @Test
    void testSetToStringsNullEntry()
    {
        final Object[] output = CollectionsHelper.setToStrings(null);
        assertThat(output).as("array should be null").isNull();
    }

    @Test
    void testSetToStringsEmptyEntry()
    {
        final Set<Object> input = new HashSet<>();
        final Object[] output = CollectionsHelper.setToStrings(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array should be empty").isEmpty();
    }

    @Test
    void testSetToStringsSingle()
    {
        final Set<Object> input = new HashSet<>();
        input.add(A);
        final String[] output = CollectionsHelper.setToStrings(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array size does not match").hasSize(1);
        assertThat(output[0]).as("element 0 does not match").isEqualTo(A);
    }

    @Test
    void testSetToStringsOrderedSet()
    {
        final Set<Object> input = new TreeSet<>();
        input.add(A);
        input.add(C);
        input.add(B);
        final String[] output = CollectionsHelper.setToStrings(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array size does not match").hasSize(3);
        assertThat(output[0]).as("element 0 does not match").isEqualTo(A);
        assertThat(output[1]).as("element 1 does not match").isEqualTo(B);
        assertThat(output[2]).as("element 2 does not match").isEqualTo(C);
    }

    @Test
    void testSetToStringsSequencialSet()
    {
        final Set<Object> input = new LinkedHashSet<>();
        input.add(A);
        input.add(C);
        input.add(B);
        final String[] output = CollectionsHelper.setToStrings(input);
        assertThat(output).as("array should not be null").isNotNull()
                .as("array size does not match").hasSize(3);
        assertThat(output[0]).as("element 0 does not match").isEqualTo(A);
        assertThat(output[1]).as("element 1 does not match").isEqualTo(C);
        assertThat(output[2]).as("element 2 does not match").isEqualTo(B);
    }

}
