/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.routing.util.EdgeLevelFilter;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class LevelGraphStorageTest extends GraphStorageTest {

    @Override
    protected LevelGraph createGraph() {
        return (LevelGraph) super.createGraph();
    }

    @Override
    protected LevelGraphStorage createGraphStorage(Directory dir) {
        return (LevelGraphStorage) super.createGraphStorage(dir);
    }

    @Override
    public GraphStorage newGraph(Directory dir) {
        return new LevelGraphStorage(dir);
    }

    @Test
    public void testCannotBeLoadedViaDifferentClass() {
        GraphStorage g = createGraphStorage(new RAMDirectory(defaultGraph, true));
        g.flush();
        g.close();

        g = new GraphBuilder().location(defaultGraph).mmap(false).store(true).create();
        try {
            g.loadExisting();
            assertTrue(false);
        } catch (Exception ex) {
        }

        g = newGraph(new RAMDirectory(defaultGraph, true));
        assertTrue(g.loadExisting());
    }

    @Test
    public void testPriosWhileDeleting() {
        LevelGraph g = createGraph();
        for (int i = 0; i < 20; i++) {
            g.setLevel(i, i);
        }
        g.markNodeRemoved(10);
        g.optimize();
        assertEquals(9, g.getLevel(9));
        assertNotSame(10, g.getLevel(10));
        assertEquals(19, g.nodes());
    }

    @Test
    public void testPrios() {
        LevelGraph g = createGraph();
        assertEquals(0, g.getLevel(10));

        g.setLevel(10, 100);
        assertEquals(100, g.getLevel(10));

        g.setLevel(30, 100);
        assertEquals(100, g.getLevel(30));
    }

    @Test
    public void testEdgeFilter() {
        LevelGraph g = createGraph();
        g.edge(0, 1, 10, true);
        g.edge(0, 2, 20, true);
        g.edge(2, 3, 30, true);
        EdgeSkipIterator tmpIter = g.edge(3, 4, 40, true);
        assertEquals(-1, tmpIter.skippedEdge());

        // shortcut
        g.edge(0, 4, 40, true);
        g.setLevel(0, 1);
        g.setLevel(4, 1);

        EdgeIterator iter = new EdgeLevelFilter(g).doFilter(g.getEdges(0));
        assertEquals(1, GraphUtility.count(iter));
        iter = g.getEdges(2);
        assertEquals(2, GraphUtility.count(iter));
    }
}
