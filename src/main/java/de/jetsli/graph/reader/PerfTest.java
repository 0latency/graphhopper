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
package de.jetsli.graph.reader;

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeSimple;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.StopWatch;
import java.util.Date;
import java.util.Random;

/**
 * Memory usage calculation according to 
 * 
 * http://www.ibm.com/developerworks/opensource/library/j-codetoheap/index.html?ca=drs
 * http://kohlerm.blogspot.de/2009/02/how-to-really-measure-memory-usage-of.html
 * 
 * TODO respect padding:
 * 
 * http://www.codeinstructions.com/2008/12/java-objects-memory-structure.html
 *
 * @author Peter Karich
 */
public class PerfTest {

    Graph g;

    public PerfTest(Graph graph) {
        g = graph;
    }
    int latMin = 497354, latMax = 501594;
    int lonMin = 91924, lonMax = 105784;
    // Try to use MemoryMeter https://github.com/jbellis/jamm

    public void start() {
        System.out.println("locations:" + g.getLocations());
        // for query: 16 entriesPerNode seems to be fast and not such a memory waste
        // approx 46 bytes/entry + sizeOf(Integer)
        // 10km search => 0.048s,~  83k nodes per search retrieved
        // 20km search => 0.173s,~ 313k
        // 40km search => 0.550s,~1031k
        
        // increase speed about 
        //  => 2% when using int   instead double    in BBox (multiplied with 1e+7 before) => but too complicated
        //  => 2% when using float instead of double in CoordTrig => but bad in other cases. if double and float implementation => too complicated

        int maxDist = 50;
        int maxEPerL = 20;
        System.out.println(new Date() + "# maxDist:" + maxDist + ", maxE/L:" + maxEPerL);

//        measureFill(maxEPerL);
        measureSearch(maxDist, maxEPerL);
    }

    private void measureFill(int maxEPerL) {
        for (int entriesPerLeaf = 1; entriesPerLeaf < maxEPerL; entriesPerLeaf *= 2) {
            final QuadTree<Integer> quadTree = new QuadTreeSimple<Integer>(entriesPerLeaf);
            fillQuadTree(quadTree, g);
            System.gc();
            System.gc();
            float mem = (float) quadTree.getMemoryUsageInBytes(1) / Helper.MB;
            System.out.println(new Date() + "# e/leaf:" + entriesPerLeaf + ", mem:" + mem);
            new MiniTest("fill") {

                @Override public long doCalc(int run) {
                    QuadTree<Integer> quadTree = new QuadTreeSimple<Integer>();
                    fillQuadTree(quadTree, g);
                    return quadTree.size();
                }
            }.setMax(20).start();
        }
    }

    private void measureSearch(int maxDist, int maxEPerL) {
        for (int distance = 10; distance < maxDist; distance *= 2) {
            for (int entriesPerLeaf = 16; entriesPerLeaf < maxEPerL; entriesPerLeaf *= 2) {
                final QuadTree<Integer> quadTree = new QuadTreeSimple<Integer>(entriesPerLeaf);
                fillQuadTree(quadTree, g);
                System.gc();
                System.gc();
                float mem = (float) quadTree.getMemoryUsageInBytes(1) / Helper.MB;
                final int tmp = distance;
                new MiniTest("neighbour search e/leaf:" + entriesPerLeaf
                        + ", dist:" + distance + ", mem:" + mem) {

                    @Override public long doCalc(int run) {
                        float lat = (random.nextInt(latMax - latMin) + latMin) / 10000.0f;
                        float lon = (random.nextInt(lonMax - lonMin) + lonMin) / 10000.0f;
                        return quadTree.getNeighbours(lat, lon, tmp).size();
                    }
                }.setMax(500).setShowProgress(true).setSeed(0).start();
            }
        }
    }

    class MiniTest {

        protected int max = 20;
        protected String name = "";
        protected boolean showProgress = true;
        protected Random random = new Random(0);

        public MiniTest() {
        }

        public MiniTest(String n) {
            name = n;
        }

        public MiniTest setShowProgress(boolean showProgress) {
            this.showProgress = showProgress;
            return this;
        }

        public MiniTest setSeed(long seed) {
            random.setSeed(seed);
            return this;
        }

        public MiniTest setMax(int max) {
            this.max = max;
            return this;
        }

        public void start() {
            int maxNo = max / 4;
            long res = 0;
            System.out.println(new Date() + "# start performance " + name + ", iterations:" + max);
            StopWatch sw = new StopWatch().start();
            for (int i = 0; i < maxNo; i++) {
                res += doJvmInit(i);
            }

            if (showProgress)
                System.out.println(new Date() + "# jvm initialized! secs/iter:" + sw.stop().getSeconds() / maxNo);

            sw = new StopWatch().start();
            maxNo = max;
            float partition = 5f;
            int part = (int) (maxNo / partition);
            for (int i = 0; i < maxNo; i++) {
                if (showProgress && i % part == 0)
                    System.out.println(new Date() + "# progress " + i * 100 / maxNo + "% => secs/iter:" + (sw.stop().start().getSeconds() / i));

                res += doCalc(i);
            }
            System.out.println(new Date() + "# progress 100% in " + sw.stop().getSeconds()
                    + " secs => secs/iter:" + sw.stop().getSeconds() / maxNo + "\n avoid jvm removal:" + res
                    + ", memInfo:" + Helper.getMemInfo() + " " + Helper.getBeanMemInfo() + "\n");
        }

        /**
         * @return something meaningless to avoid that jvm optimizes away the inner code
         */
        public long doJvmInit(int run) {
            return doCalc(-run);
        }

        public long doCalc(int run) {
            return run;
        }
    }

    public static void fillQuadTree(QuadTree quadTree, Graph graph) {
        // TODO LATER persist quad tree to make things faster and store osm ids instead nothing
        Integer empty = new Integer(1);
        int locs = graph.getLocations();
        for (int i = 0; i < locs; i++) {
            quadTree.put(graph.getLatitude(i), graph.getLongitude(i), empty);
        }
    }
}
