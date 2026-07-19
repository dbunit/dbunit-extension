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

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 25, 2005
 */

public class DepthFirstSearchTest extends AbstractSearchTestCase
{

    @Test
    public void testEmptyGraph() throws Exception
    {
        doIt();
    }

    @Test
    public void testSingleNode() throws Exception
    {
        setInput(new String[] {A});
        setOutput(new String[] {A});
        doIt();
    }

    @Test
    public void testSingleEdge() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        setOutput(new String[] {B, A});
        doIt();
    }

    @Test
    public void testSingleEdgeMultipleInput() throws Exception
    {
        setInput(new String[] {A, B});
        addEdges(A, new String[] {B});
        setOutput(new String[] {B, A});
        doIt();
    }

    @Test
    public void testSingleEdgeRepeatedInput() throws Exception
    {
        setInput(new String[] {A, B, B, A, B});
        addEdges(A, new String[] {B});
        setOutput(new String[] {B, A});
        doIt();
    }

    @Test
    public void testDisconnected() throws Exception
    {
        setInput(new String[] {A, C});
        addEdges(A, new String[] {B});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    @Test
    public void testDisconnectedInverseOrder() throws Exception
    {
        setInput(new String[] {C, A});
        addEdges(A, new String[] {B});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    @Test
    public void testMultipleEdgesOneSource() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B, C});
        setOutput(new String[] {B, C, A});
        doIt();
    }

    @Test
    public void testMultipleEdgesMultipleSources() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B, C});
        addEdges(B, new String[] {D, C});
        setOutput(new String[] {C, D, B, A});
        doIt();
    }

    @Test
    public void testMultipleEdgesCycleFromA() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {C, B, A});
        doIt();
    }

    @Test
    public void testMultipleEdgesCycleFromB() throws Exception
    {
        setInput(new String[] {B});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {A, C, B});
        doIt();
    }

    @Test
    public void testMultipleEdgesCycleFromBA() throws Exception
    {
        setInput(new String[] {B, A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {C, B, A});
        doIt();
    }

    @Test
    public void testSelfCyclic() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {A});
        setOutput(new String[] {A});
        doIt();
    }

    @Test
    public void testCyclicAndSelfCyclic() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {A, B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {C, B, A});
        doIt();
    }

    @Test
    public void testDisconnectedCycles() throws Exception
    {
        setInput(new String[] {A, D});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        addEdges(D, new String[] {E});
        addEdges(E, new String[] {F});
        addEdges(F, new String[] {D});
        setOutput(new String[] {F, E, D, C, B, A});
        doIt();
    }

    @Test
    public void testConnectedCycle() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A, D});
        addEdges(D, new String[] {E});
        addEdges(E, new String[] {C});
        setOutput(new String[] {E, D, C, B, A});
        doIt();
    }

    @Test
    public void testBigConnectedCycle() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A, D});
        addEdges(D, new String[] {E, B});
        addEdges(E, new String[] {C});
        setOutput(new String[] {E, D, C, B, A});
        doIt();
    }

    @Test
    public void testMerge() throws Exception
    {
        setInput(new String[] {A, C});
        addEdges(A, new String[] {B});
        addEdges(C, new String[] {A});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    @Test
    void testSearch_depthLimitOne_returnsOnlyDirectNodes() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});

        final Set actual = new DepthFirstSearch(1).search(fInput, getCallback());

        assertThat(actual)
                .as("Depth-limited search of 1 on A->B->C from {A} should include the direct dependency B but not the transitive dependency C.")
                .containsExactlyInAnyOrder(A, B);
    }

    @Test
    void testSearch_depthLimitOne_multipleChildren_allDirectIncluded() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B, C});
        addEdges(B, new String[] {D});

        final Set actual = new DepthFirstSearch(1).search(fInput, getCallback());

        assertThat(actual)
                .as("Depth-limited search of 1 from A with two direct children B and C should include both, and not B's child D.")
                .containsExactlyInAnyOrder(A, B, C);
    }

    @Test
    void testSearch_depthLimitTwo_stopsAtGrandchildren() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {D});

        final Set actual = new DepthFirstSearch(2).search(fInput, getCallback());

        assertThat(actual)
                .as("Depth-limited search of 2 on A->B->C->D from {A} should reach the grandchild C but not the great-grandchild D.")
                .containsExactlyInAnyOrder(A, B, C);
    }

}
