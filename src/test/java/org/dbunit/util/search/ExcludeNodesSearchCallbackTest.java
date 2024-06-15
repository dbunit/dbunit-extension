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

public class ExcludeNodesSearchCallbackTest extends AbstractSearchTestCase
{

    private ISearchCallback callback;

    protected ISearchCallback getCallback()
    {
        return this.callback;
    }

    protected void setDenied(Object[] deniedNodes)
    {
        this.callback = new AbstractExcludeNodesSearchCallback(deniedNodes)
        {

            public SortedSet getEdges(Object fromNode) throws SearchException
            {
                return getEdgesFromNode(fromNode);
            }
        };

    }

    public void testSingleNode() throws Exception
    {
        setInput(new String[] {A});
        setDenied(new String[] {A});
        doIt();
    }

    public void testSingleEdgeDeniedA() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        setDenied(new String[] {A});
        doIt();
    }

    public void testSingleEdgeDeniedB() throws Exception
    {
        setInput(new String[] {A});
        addEdges(A, new String[] {B});
        setDenied(new String[] {B});
        setOutput(new String[] {A});
        doIt();
    }

    public void testSingleEdgeMultipleInputDeniedB() throws Exception
    {
        setInput(new String[] {A, B});
        addEdges(A, new String[] {B});
        setDenied(new String[] {B});
        setOutput(new String[] {A});
        doIt();
    }

    public void testSingleEdgeMultipleInputDeniedA() throws Exception
    {
        setInput(new String[] {A, B});
        addEdges(A, new String[] {B});
        setDenied(new String[] {A});
        setOutput(new String[] {B});
        doIt();
    }

    public void testDisconnected() throws Exception
    {
        setInput(new String[] {A, C});
        addEdges(A, new String[] {B});
        setDenied(new String[] {B, A});
        setOutput(new String[] {C});
        doIt();
    }

    public void testDisconnectedAllowedC() throws Exception
    {
        setInput(new String[] {A, C});
        addEdges(A, new String[] {B});
        setDenied(new String[] {C});
        setOutput(new String[] {B, A});
        doIt();
    }

}
