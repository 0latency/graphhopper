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
package com.graphhopper.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import gnu.trove.list.array.TIntArrayList;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphUtilityTest {

    Graph createGraph() {
        return new GraphStorage(new RAMDirectory()).createNew(10);
    }

    Graph initUnsorted(Graph g) {
        g.setNode(0, 0, 1);
        g.setNode(1, 2.5, 4.5);
        g.setNode(2, 4.5, 4.5);
        g.setNode(3, 3, 0.5);
        g.setNode(4, 2.8, 2.8);
        g.setNode(5, 4.2, 1.6);
        g.setNode(6, 2.3, 2.2);
        g.setNode(7, 5, 1.5);
        g.setNode(8, 4.6, 4);
        g.edge(8, 2, 0.5, true);
        g.edge(7, 3, 2.1, false);
        g.edge(1, 0, 3.9, true);
        g.edge(7, 5, 0.7, true);
        g.edge(1, 2, 1.9, true);
        g.edge(8, 1, 2.05, true);
        return g;
    }

    @Test
    public void testSort() {
        Graph g = initUnsorted(createGraph());
        Graph newG = GraphUtility.sort(g, createGraph(), 16);
        assertEquals(g.getNodes(), newG.getNodes());
        assertEquals(0, newG.getLatitude(0), 1e-4); // 0
        assertEquals(2.3, newG.getLatitude(1), 1e-4); // 6
        assertEquals(2.5, newG.getLatitude(2), 1e-4); // 1
        assertEquals(3, newG.getLatitude(3), 1e-4); // 3
        assertEquals(5, newG.getLatitude(4), 1e-4); // 7
        assertEquals(4.2, newG.getLatitude(5), 1e-4); // 5
        assertEquals(2.8, newG.getLatitude(6), 1e-4); // 4
    }

    @Test
    public void testSort2() {
        Graph g = initUnsorted(createGraph());
        Graph newG = GraphUtility.sortDFS(g, createGraph());
        // TODO does not handle subnetworks
        // assertEquals(g.getNodes(), newG.getNodes());
        assertEquals(0, newG.getLatitude(0), 1e-4); // 0
        assertEquals(2.5, newG.getLatitude(1), 1e-4); // 1        
        assertEquals(4.5, newG.getLatitude(2), 1e-4); // 2
        assertEquals(4.6, newG.getLatitude(3), 1e-4); // 8
    }

    @Test
    public void testSortDirected() {
        Graph g = createGraph();
        g.setNode(0, 0, 1);
        g.setNode(1, 2.5, 2);
        g.setNode(2, 3.5, 3);
        g.edge(0, 1, 1.1, false);
        g.edge(2, 1, 1.1, false);
        GraphUtility.sortDFS(g, createGraph());
    }

    @Test
    public void testCopy() {
        Graph g = initUnsorted(createGraph());
        EdgeIterator iter = g.edge(6, 5, 11, true);
        iter.pillarNodes(Helper.createPointList(12, 10, -1, 3));
        LevelGraph lg = (LevelGraph) new LevelGraphStorage(new RAMDirectory()).createNew(10);
        GraphUtility.copyTo(g, lg);
        iter = lg.getEdgeProps(iter.edge(), 6);
        assertEquals(Helper.createPointList(-1, 3, 12, 10), iter.pillarNodes());
        assertEquals(0, lg.getLevel(0));
        assertEquals(0, lg.getLevel(1));
        assertEquals(0, lg.getLatitude(0), 1e-6);
        assertEquals(1, lg.getLongitude(0), 1e-6);
        assertEquals(2.5, lg.getLatitude(1), 1e-6);
        assertEquals(4.5, lg.getLongitude(1), 1e-6);
        assertEquals(9, lg.getNodes());
        iter = lg.getOutgoing(8);
        iter.next();
        assertEquals(2.05, iter.distance(), 1e-6);
        assertEquals("11", BitUtil.toBitString(iter.flags(), 2));
        iter.next();
        assertEquals(0.5, iter.distance(), 1e-6);
        assertEquals("11", BitUtil.toBitString(iter.flags(), 2));

        iter = lg.getOutgoing(7);
        iter.next();
        assertEquals(2.1, iter.distance(), 1e-6);
        assertEquals("01", BitUtil.toBitString(iter.flags(), 2));
        assertTrue(iter.next());
        assertEquals(0.7, iter.distance(), 1e-6);
    }
}
