/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.routing.rideshare;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.GraphUtility;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich,
 */
public class DijkstraShortestOf2ToPubTest {

    Graph getGraph() {
        return AbstractRoutingAlgorithmTester.getMatrixAlikeGraph();
    }

    public RoutingAlgorithm createDijkstra(Graph g) {
        return new DijkstraWhichToOne(g);
    }

    @Test
    public void testCalcShortestPath() {
        Graph g = GraphUtility.clone(getGraph());
        DijkstraShortestOf2ToPub d = new DijkstraShortestOf2ToPub(g);
        d.addPubTransportPoints(DijkstraWhichToOneTest.pubTransportPath);
        int from = 13;
        int dest = 65;
        d.setFrom(from);
        d.setTo(dest);
        Path path = d.calcShortestPath();
        assertWithBiDijkstra(DijkstraWhichToOneTest.pubTransportPath, path, from, dest, g);
    }

    @Test
    public void testCalcShortestPath2() {
        Graph g = GraphUtility.clone(getGraph());
        DijkstraShortestOf2ToPub d = new DijkstraShortestOf2ToPub(g);
        d.addPubTransportPoints(DijkstraWhichToOneTest.pubTransportPath);
        int from = 13;
        int dest = 70;
        d.setFrom(from);
        d.setTo(dest);

        Path path = d.calcShortestPath();
        assertWithBiDijkstra(DijkstraWhichToOneTest.pubTransportPath, path, from, dest, g);
    }

    @Test
    public void testCalculateShortestPathWithSpecialFinishCondition() {
        int[] pubTransport = new int[]{20, 21, 31, 41, 51, 52, 62, 72};
        Graph g = GraphUtility.clone(getGraph());
        g.edge(21, 31, 100, true);
        g.edge(31, 41, 100, true);
        g.edge(41, 51, 100, true);
        g.edge(51, 52, 100, true);
        DijkstraShortestOf2ToPub d = new DijkstraShortestOf2ToPub(g);
        d.addPubTransportPoints(pubTransport);
        int from = 1;
        int dest = 53;
        d.setFrom(from);
        d.setTo(dest);
        Path path = d.calcShortestPath();
        assertWithBiDijkstra(pubTransport, path, from, dest, g);
    }

    private void assertWithBiDijkstra(int[] points, Path path, int from, int to, Graph g) {
        Path bestManualPathFrom = null;
        Path bestManualPathTo = null;
        for (int i = 0; i < points.length; i++) {
            Path manualFrom = new DijkstraBidirectionRef(g).calcPath(points[i], from);
            Path manualTo = new DijkstraBidirectionRef(g).calcPath(points[i], to);
            if (bestManualPathFrom == null
                    || manualFrom.weight() + manualTo.weight()
                    < bestManualPathFrom.weight() + bestManualPathTo.weight()) {
                bestManualPathFrom = manualFrom;
                bestManualPathTo = manualTo;
            }
        }

        assertEquals(bestManualPathFrom.nodes() + bestManualPathTo.nodes() - 1, path.nodes());
        assertEquals(bestManualPathFrom.weight() + bestManualPathTo.weight(), path.weight(), 1e-3);
    }
}
