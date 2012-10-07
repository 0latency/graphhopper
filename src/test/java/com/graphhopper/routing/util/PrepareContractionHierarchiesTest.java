/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {

    LevelGraphStorage createGraph() {
        LevelGraphStorage g = new LevelGraphStorage(new RAMDirectory());
        g.createNew(10);

        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(0, 4, 3, true);
        g.edge(1, 2, 2, true);
        g.edge(2, 3, 1, true);
        g.edge(4, 3, 2, true);
        g.edge(5, 1, 2, true);
        return g;
    }

    @Test
    public void testShortestPathSkipNode() {
        Graph g = createGraph();
        double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
        PrepareContractionHierarchies.LocalShortestPathCH algo = new PrepareContractionHierarchies.LocalShortestPathCH(g, 3);
        Path p = algo.clear().setLimit(10).calcPath(4, 2);
        assertTrue(p.distance() > normalDist);
    }

    @Test
    public void testShortestPathLimit() {
        Graph g = createGraph();
        PrepareContractionHierarchies.LocalShortestPathCH algo = new PrepareContractionHierarchies.LocalShortestPathCH(g, 0);
        Path p = algo.clear().setLimit(2).calcPath(4, 1);
        assertNull(p);
    }

    @Test
    public void testAddShortcuts() {
        LevelGraphStorage g = createGraph();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(g);
        prepare.doWork();
        EdgeSkipIterator iter = g.getAllEdges();
        while (iter.next()) {
            System.out.println(iter.fromNode() + "->" + iter.node()
                    + ", dist: " + (float) iter.distance() + ", skip:" + iter.skippedNode()
                    + ", level:" + g.getLevel(iter.fromNode()) + "->" + g.getLevel(iter.node()));
        }
        assertEquals(7 + 6, GraphUtility.count(g.getAllEdges()));
        assertEquals(3, GraphUtility.count(g.getEdges(5)));
        assertEquals(4, GraphUtility.count(g.getEdges(0)));
    }
}
