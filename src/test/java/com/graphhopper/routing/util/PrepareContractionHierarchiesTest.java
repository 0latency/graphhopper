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
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.PrepareContractionHierarchies.NodeCH;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    List<NodeCH> createGoals(int... gNodes) {
        List<NodeCH> goals = new ArrayList<NodeCH>();
        for (int i = 0; i < gNodes.length; i++) {
            NodeCH n = new NodeCH();
            n.node = gNodes[i];
            goals.add(n);
        }
        return goals;
    }

    @Test
    public void testShortestPathSkipNode() {
        LevelGraph g = createGraph();
        double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
        PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g)
                .setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setSkipNode(3));
        List<NodeCH> gs = createGoals(2);
        algo.clear().setLimit(10).calcPath(4, gs);
        Path p = algo.extractPath(gs.get(0).entry);
        assertTrue(p.distance() > normalDist);
    }

    @Test
    public void testShortestPathSkipNode2() {
        LevelGraph g = createGraph();
        double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
        PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g).
                setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setSkipNode(3));
        List<NodeCH> gs = createGoals(1, 2);
        algo.clear().setLimit(10).calcPath(4, gs);
        Path p = algo.extractPath(gs.get(1).entry);
        assertTrue(p.distance() > normalDist);
    }

    @Test
    public void testShortestPathLimit() {
        LevelGraph g = createGraph();
        PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g)
                .setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setSkipNode(0));
        List<NodeCH> gs = createGoals(1);
        algo.clear().setLimit(2).calcPath(4, gs);
        assertNull(gs.get(0).entry);
    }

    @Test
    public void testAddShortcuts() {
        LevelGraph g = createGraph();
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(g);
        prepare.doWork();
        assertEquals(old, GraphUtility.count(g.getAllEdges()));
//        assertEquals(3, GraphUtility.count(g.getEdges(5)));
//        assertEquals(4, GraphUtility.count(g.getEdges(0)));
    }
    
    @Test
    public void testMoreComplexGraph() {
        LevelGraph g = PrepareLongishPathShortcutsTest.createShortcutsGraph();
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(g);
        prepare.doWork();
        assertEquals(old + 6, GraphUtility.count(g.getAllEdges()));
    }

    @Test
    public void testDirectedGraph() {
        LevelGraphStorage g = new LevelGraphStorage(new RAMDirectory());
        g.createNew(10);
        g.edge(5, 4, 3, false);
        g.edge(4, 5, 10, false);
        g.edge(2, 4, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(4, 3, 1, false);
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(g);
        prepare.doWork();
        assertEquals(old + 2, GraphUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();

        Path p = algo.clear().calcPath(4, 2);
        assertEquals(3, p.distance(), 1e-6);
        assertEquals(Arrays.asList(4, 3, 5, 2), p.toNodeList());
    }

    @Test
    public void testDirectedGraph2() {
        LevelGraphStorage g = new LevelGraphStorage(new RAMDirectory());
        g.createNew(10);
        PrepareLongishPathShortcutsTest.initDirected2(g);
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(g);
        prepare.doWork();
        // PrepareLongishPathShortcutsTest.printEdges(g);
        assertEquals(old + 14, GraphUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();

        Path p = algo.clear().calcPath(0, 10);
        assertEquals(10, p.distance(), 1e-6);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.toNodeList());
    }
}
