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

import java.util.SortedSet;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 25, 2005
 */

public class BiDirectionalEdgesDepthFirstSearchTest extends DepthFirstSearchTest
{

    protected ISearchCallback getCallback()
    {
        return new ISearchCallback()
        {
            public SortedSet getEdges(Object fromNode)
            {
                SortedSet fromSet = getEdgesFromNode(fromNode);
                SortedSet toSet = getEdgesToNode(fromNode);
                if (fromSet == null)
                {
                    return toSet;
                }
                if (toSet != null)
                {
                    fromSet.addAll(toSet);
                }
                return fromSet;
            };

            public void nodeAdded(Object fromNode)
            {
            }

            public boolean searchNode(Object node)
            {
                return true;
            }
        };
    };

    // more tests, now adding nodes or graphs that would not directly reachable
    // using unidirectional edges.
    // For, instance, on the graph C->A->B, C would never be reached from A or B

    public void testSingleReverseEdge() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(C, new String[] {A});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    public void testSingleReverseEdgeInputB() throws Exception
    {
        setInput(new String[] {B});
        addEdges(A, new String[] {B});
        addEdges(C, new String[] {A});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    public void testSingleReverseEdgeMultipleInput() throws Exception
    {
        setInput(new String[] {B, A});
        addEdges(A, new String[] {B});
        addEdges(C, new String[] {A});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    public void testSingleReverseEdgeMultipleInputIncludingC() throws Exception
    {
        setInput(new String[] {C, B, A});
        addEdges(A, new String[] {B});
        addEdges(C, new String[] {A});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    public void testOneInputTwoEdges() throws Exception
    {
        setInput(new String[] {B});
        addEdges(A, new String[] {C});
        addEdges(B, new String[] {C});
        setOutput(new String[] {C, B, A});
        doIt();
    }

    // TODO: continue adding more tests uncommenting and adapting tests below...
    /*
     * 
     * public void testSingleEdgeRepeatedInput() throws Exception { setInput(new
     * String[] { A, B, B, A, B }); addEdges(A, new String[] { B });
     * setOutput(new String[] { B, A }); doIt(); }
     * 
     * public void testDisconnected() throws Exception { setInput(new String[] {
     * A, C }); addEdges(A, new String[] { B }); setOutput(new String[] { B, A,
     * C }); doIt(); }
     * 
     * public void testDisconnectedInverseOrder() throws Exception {
     * setInput(new String[] { C, A }); addEdges(A, new String[] { B });
     * setOutput(new String[] { B, A, C }); doIt(); }
     * 
     * public void testMultipleEdgesOneSource() throws Exception { setInput(new
     * String[] { A }); addEdges(A, new String[] { B, C }); setOutput(new
     * String[] { B, C, A }); doIt(); }
     * 
     * public void testMultipleEdgesMultipleSources() throws Exception {
     * setInput(new String[] { A }); addEdges(A, new String[] { B, C });
     * addEdges(B, new String[] { D, C }); setOutput(new String[] { C, D, B, A
     * }); doIt(); }
     */
    public void testMultipleEdgesCycleFromA() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {A, C, B});
        doIt();
    }

    public void testMultipleEdgesCycleFromB() throws Exception
    {
        setInput(new String[] {B});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {B, A, C});
        doIt();
    }

    public void testMultipleEdgesCycleFromBA() throws Exception
    {
        setInput(new String[] {B, A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {A, C, B});
        doIt();
    }

    /*
     * public void testSelfCyclic() throws Exception { setInput(new String[] { A
     * }); addEdges(A, new String[] { A }); setOutput(new String[] { A });
     * doIt(); }
     * 
     */
    public void testCyclicAndSelfCyclic() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {A, B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        setOutput(new String[] {A, C, B});
        doIt();
    }

    public void testDisconnectedCycles() throws Exception
    {
        setInput(new String[] {A, D});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A});
        addEdges(D, new String[] {E});
        addEdges(E, new String[] {F});
        addEdges(F, new String[] {D});
        setOutput(new String[] {D, F, E, A, C, B});
        doIt();
    }

    public void testConnectedCycle() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A, D});
        addEdges(D, new String[] {E});
        addEdges(E, new String[] {C});
        setOutput(new String[] {A, E, D, C, B});
        doIt();
    }

    public void testBigConnectedCycle() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        addEdges(B, new String[] {C});
        addEdges(C, new String[] {A, D});
        addEdges(D, new String[] {E, B});
        addEdges(E, new String[] {C});
        setOutput(new String[] {A, C, B, E, D});
        doIt();
    }

}
