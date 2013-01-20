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

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import java.io.File;
import java.util.Random;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class Location2IDQuadtreeTest {

    String location = "./target/tmp/";

    public Location2IDIndex createIndex(Graph g, int resolution) {
        return new Location2IDQuadtree(g, new MMapDirectory(location + "loc2idIndex")).prepareIndex(resolution);
    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(location));
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File(location));
    }

    @Test
    public void testSimpleGraph() {
        Graph g = createGraph();
        g.setNode(0, -1, -2);
        g.setNode(1, 2, -1);
        g.setNode(2, 0, 1);
        g.setNode(3, 1, 2);
        g.setNode(4, 6, 1);
        g.setNode(5, 4, 4);
        g.setNode(6, 4.5, -0.5);
        g.edge(0, 1, 3.5, true);
        g.edge(0, 2, 2.5, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 2.2, true);
        g.edge(1, 4, 2.4, true);
        g.edge(3, 5, 1.5, true);

        Location2IDIndex idx = createIndex(g, 8);
        assertEquals(4, idx.findID(5, 2));
        assertEquals(3, idx.findID(1.5, 2));
        assertEquals(0, idx.findID(-1, -1));
        // now get the edge 1-4 and not node 6
        assertEquals(6, idx.findID(4, 0));
    }

    @Test
    public void testSimpleGraph2() {
        //  6         4
        //  5       
        //          6
        //  4                5
        //  3
        //  2      1  
        //  1            3
        //  0      2      
        // -1   0
        //
        //     -2 -1 0 1 2 3 4
        Graph g = createGraph();
        g.setNode(0, -1, -2);
        g.setNode(1, 2, -1);
        g.setNode(2, 0, 1);
        g.setNode(3, 1, 2);
        g.setNode(4, 6, 1);
        g.setNode(5, 4, 4);
        g.setNode(6, 4.5, -0.5);
        g.edge(0, 1, 3.5, true);
        g.edge(0, 2, 2.5, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 3.2, true);
        g.edge(1, 4, 2.4, true);
        g.edge(3, 5, 1.5, true);

        Location2IDIndex idx = createIndex(g, 28);
        assertEquals(4, idx.findID(5, 2));
        assertEquals(3, idx.findID(1.5, 2));
        assertEquals(0, idx.findID(-1, -1));
        assertEquals(6, idx.findID(4, 0));
        assertEquals(6, idx.findID(4, -2));
        // 4 is wrong!
        assertEquals(6, idx.findID(4, 1));
        assertEquals(5, idx.findID(3, 3));
    }

    @Test
    public void testSinglePoints120() {
        Graph g = createSampleGraph();
        Location2IDIndex idx = createIndex(g, 120);

        // maxWidth is ~555km and with size==8 it will be exanded to 4*4 array => maxRasterWidth==555/4
        // assertTrue(idx.getMaxRasterWidthKm() + "", idx.getMaxRasterWidthKm() < 140);
        assertEquals(1, idx.findID(1.637, 2.23));
        assertEquals(10, idx.findID(3.649, 1.375));
        assertEquals(9, idx.findID(3.3, 2.2));
        assertEquals(6, idx.findID(3.0, 1.5));

        assertEquals(10, idx.findID(3.8, 0));
        assertEquals(10, idx.findID(3.8466, 0.021));
    }

    @Test
    public void testGrid() {
        Graph g = createSampleGraph();
        int locs = g.nodes();

        Location2IDIndex memoryEfficientIndex = createIndex(g, 120);
        // if we would use less array entries then some points gets the same key so avoid that for this test
        // e.g. for 16 we get "expected 6 but was 9" i.e 6 was overwritten by node j9 which is a bit closer to the grid center        
        // go through every point of the graph if all points are reachable
        for (int i = 0; i < locs; i++) {
            double lat = g.getLatitude(i);
            double lon = g.getLongitude(i);
            assertEquals("nodeId:" + i + " " + (float) lat + "," + (float) lon,
                    i, memoryEfficientIndex.findID(lat, lon));
        }

        // hit random lat,lon and compare result to full index
        Random rand = new Random(12);
        Location2IDIndex fullIndex = new Location2IDFullIndex(g);
        DistanceCalc dist = new DistanceCalc();
        for (int i = 0; i < 100; i++) {
            double lat = rand.nextDouble() * 5;
            double lon = rand.nextDouble() * 5;
            int fullId = fullIndex.findID(lat, lon);
            double fullLat = g.getLatitude(fullId);
            double fullLon = g.getLongitude(fullId);
            float fullDist = (float) dist.calcDist(lat, lon, fullLat, fullLon);
            int newId = memoryEfficientIndex.findID(lat, lon);
            double newLat = g.getLatitude(newId);
            double newLon = g.getLongitude(newId);
            float newDist = (float) dist.calcDist(lat, lon, newLat, newLon);

            // conceptual limitation where we are stuck in a blind alley limited
            // to the current tile
            if (i == 6 || i == 36 || i == 90 || i == 96)
                continue;

            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
                    + " full:" + fullLat + "," + fullLon + " fullDist:" + fullDist
                    + " found:" + newLat + "," + newLon + " foundDist:" + newDist,
                    Math.abs(fullDist - newDist) < 50000);
        }
    }

    @Test
    public void testSinglePoints32() {
        Graph g = createSampleGraph();
        Location2IDIndex idx = createIndex(g, 32);

        // 10 or 6
        assertEquals(10, idx.findID(3.649, 1.375));
        assertEquals(10, idx.findID(3.8465748, 0.021762699));
        assertEquals(6, idx.findID(2.485, 1.373));
        assertEquals(0, idx.findID(0.64628404, 0.53006625));
    }

    @Test
    public void testNormedDist() {
        Location2IDQuadtree index = new Location2IDQuadtree(createGraph(), new RAMDirectory());
        index.initAlgo(5, 6);
        assertEquals(1, index.normedDist(0, 1), 1e-6);
        assertEquals(2, index.normedDist(0, 7), 1e-6);
        assertEquals(2, index.normedDist(7, 2), 1e-6);
        assertEquals(1, index.normedDist(7, 1), 1e-6);
        assertEquals(4, index.normedDist(13, 25), 1e-6);
        assertEquals(8, index.normedDist(15, 25), 1e-6);
    }

    @Test
    public void testNoErrorOnEdgeCase_lastIndex() {
        int locs = 10000;
        Graph g = new GraphBuilder().location(location).mmap(true).create();
        Random rand = new Random(12);
        for (int i = 0; i < locs; i++) {
            g.setNode(i, (float) rand.nextDouble() * 10 + 10, (float) rand.nextDouble() * 10 + 10);
        }
        createIndex(g, 200);
        Helper.removeDir(new File(location));
    }

    public static Graph createGraph() {
        return new GraphBuilder().create();
    }

    public static Graph createSampleGraph() {
        Graph graph = createGraph();
        // length does not matter here but lat,lon and outgoing edges do!

//        
//   lat             /--------\
//    5   o-        p--------\ q
//          \  /-----\-----n | |
//    4       k    /--l--    m/                 
//           / \  j      \   |              
//    3     |   g  \  h---i  /             
//          |       \    /  /             
//    2     e---------f--  /
//                   /  \-d
//    1        /--b--      \               
//            |    \--------c
//    0       a                  
//        
//   lon: 0   1   2   3   4   5

        int a0 = 0;
        graph.setNode(0, 0, 1.0001f);
        int b1 = 1;
        graph.setNode(1, 1, 2);
        int c2 = 2;
        graph.setNode(2, 0.5f, 4.5f);
        int d3 = 3;
        graph.setNode(3, 1.5f, 3.8f);
        int e4 = 4;
        graph.setNode(4, 2.01f, 0.5f);
        int f5 = 5;
        graph.setNode(5, 2, 3);
        int g6 = 6;
        graph.setNode(6, 3, 1.5f);
        int h7 = 7;
        graph.setNode(7, 2.99f, 3.01f);
        int i8 = 8;
        graph.setNode(8, 3, 4);
        int j9 = 9;
        graph.setNode(9, 3.3f, 2.2f);
        int k10 = 10;
        graph.setNode(10, 4, 1);
        int l11 = 11;
        graph.setNode(11, 4.1f, 3);
        int m12 = 12;
        graph.setNode(12, 4, 4.5f);
        int n13 = 13;
        graph.setNode(13, 4.5f, 4.1f);
        int o14 = 14;
        graph.setNode(14, 5, 0);
        int p15 = 15;
        graph.setNode(15, 4.9f, 2.5f);
        int q16 = 16;
        graph.setNode(16, 5, 5);
        // => 17 locations

        graph.edge(a0, b1, 1, true);
        graph.edge(c2, b1, 1, true);
        graph.edge(c2, d3, 1, true);
        graph.edge(f5, b1, 1, true);
        graph.edge(e4, f5, 1, true);
        graph.edge(m12, d3, 1, true);
        graph.edge(e4, k10, 1, true);
        graph.edge(f5, d3, 1, true);
        graph.edge(f5, i8, 1, true);
        graph.edge(f5, j9, 1, true);
        graph.edge(k10, g6, 1, true);
        graph.edge(j9, l11, 1, true);
        graph.edge(i8, l11, 1, true);
        graph.edge(i8, h7, 1, true);
        graph.edge(k10, n13, 1, true);
        graph.edge(k10, o14, 1, true);
        graph.edge(l11, p15, 1, true);
        graph.edge(m12, p15, 1, true);
        graph.edge(q16, p15, 1, true);
        graph.edge(q16, m12, 1, true);
        return graph;
    }
}
