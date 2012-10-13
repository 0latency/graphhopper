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
package com.graphhopper.routing.util;

import com.graphhopper.coll.MySortedCollection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.Path4Level;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.StopWatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class prepares the graphs to be used from PrepareContractionHierarchies algorithm.
 *
 * There are several description of contraction hierarchies available only but this is one of the
 * more detailed: http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchies {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private WeightCalculation weightCalc;
    private LevelGraph g;
    // the most important nodes comes last
    private MySortedCollection sortedNodes;
    private WeightedNode refs[];
    // shortcut is in one direction, speed is ignored - see prepareEdges
    private static int scOneDir = CarStreetType.flags(0, false);
    private static int scBothDir = CarStreetType.flags(0, true);
    private Map<Long, Shortcut> shortcuts = new HashMap<Long, Shortcut>();
    private EdgeLevelFilterCH edgeFilter;
    private OneToManyDijkstraCH algo;

    public PrepareContractionHierarchies(LevelGraph g) {
        this.g = g;
        sortedNodes = new MySortedCollection(g.getNodes());
        refs = new WeightedNode[g.getNodes()];
        weightCalc = FastestCalc.DEFAULT;
        edgeFilter = new EdgeLevelFilterCH(g);
    }

    private static class WeightedNode {

        int node;
        int priority;

        public WeightedNode(int node, int priority) {
            this.node = node;
            this.priority = priority;
        }

        @Override public String toString() {
            return node + " (" + priority + ")";
        }
    }

    private static class Shortcut {

        int from;
        int to;
        double distance;
        boolean update = false;
        int originalEdges;
        int flags = scOneDir;

        public Shortcut(int from, int to, double dist) {
            this.from = from;
            this.to = to;
            this.distance = dist;
        }

        @Override public String toString() {
            return from + "->" + to + ", dist:" + distance + ",update:" + update;
        }
    }

    static class NodeCH {

        int node;
        int originalEdges;
        EdgeEntry entry;
        double distance;

        @Override public String toString() {
            return "" + node;
        }
    }

    public void doWork() {
        // TODO integrate PrepareRoutingShortcuts -> so avoid all nodes with negative level in the other methods        
        // in PrepareShortcuts level 0 and -1 is already used move that to level 1 and 2 so that level 0 stays as uncontracted
        prepareEdges();
        prepareNodes();
        contractNodes();
    }

    void prepareEdges() {
        // in CH the flags will be ignored (calculating the new flags for the shortcuts is impossible)
        // also several shortcuts would be necessary with the different modes (e.g. fastest and shortest)
        // so calculate the weight and store this as distance, then use only distance instead of getWeight
        EdgeSkipIterator iter = g.getAllEdges();
        while (iter.next()) {
            iter.distance(weightCalc.getWeight(iter));
            iter.originalEdges(1);
        }
    }

    void prepareNodes() {
        int len = g.getNodes();

        // minor idea: 1. sort nodes randomly and 2. pre-init with node degree
        for (int node = 0; node < len; node++) {
            refs[node] = new WeightedNode(node, 0);
        }

        for (int node = 0; node < len; node++) {
            WeightedNode wn = refs[node];
            wn.priority = calculatePriority(node);
            // System.out.println(wn);
            sortedNodes.insert(wn.node, wn.priority);
        }

        if (sortedNodes.isEmpty())
            throw new IllegalStateException("no nodes found!?");
        // System.out.println("-----------");
    }

    void contractNodes() {
        int level = 1;
        int newShortcuts = 0;
        final int updateSize = Math.max(10, sortedNodes.size() / 10);
        int counter = 0;
        int updateCounter = 0;
        StopWatch sw = new StopWatch();
        // no update all => 600k shortcuts and 3min
        while (!sortedNodes.isEmpty()) {
            if (counter % updateSize == 0) {
                // periodically update priorities of ALL nodes            
                if (updateCounter > 0 && updateCounter % 2 == 0) {
                    int len = g.getNodes();
                    sw.start();
                    // TODO avoid to traverse all nodes -> via a new sortedNodes.iterator()
                    for (int node = 0; node < len; node++) {
                        WeightedNode wNode = refs[node];
                        if (g.getLevel(node) != 0)
                            continue;
                        int old = wNode.priority;
                        wNode.priority = calculatePriority(node);
                        sortedNodes.update(node, old, wNode.priority);
                    }
                    sw.stop();
                }
                updateCounter++;
                logger.info(counter + ", nodes: " + sortedNodes.size() + ", shortcuts:" + newShortcuts + ", updateAllTime:" + sw.getSeconds() + ", " + updateCounter);
            }

            counter++;
            WeightedNode wn = refs[sortedNodes.pollKey()];

            // update priority of current node via simulating 'addShortcuts'
            wn.priority = calculatePriority(wn.node);
            if (!sortedNodes.isEmpty() && wn.priority > sortedNodes.peekValue()) {
                // node got more important => insert as new value and contract it later
                sortedNodes.insert(wn.node, wn.priority);
                continue;
            }

            // contract!            
            newShortcuts += addShortcuts(wn.node);
            g.setLevel(wn.node, level);
            level++;

            // recompute priority of uncontracted neighbors
            EdgeIterator iter = g.getEdges(wn.node);
            while (iter.next()) {
                if (g.getLevel(iter.node()) != 0)
                    // already contracted no update necessary
                    continue;

                int nn = iter.node();
                WeightedNode neighborWn = refs[nn];
                int tmpOld = neighborWn.priority;
                neighborWn.priority = calculatePriority(nn);
                if (neighborWn.priority != tmpOld)
                    sortedNodes.update(nn, tmpOld, neighborWn.priority);
            }
        }
        System.out.println("new shortcuts " + newShortcuts);
    }

    /**
     * Calculates the priority of node v without changing the graph.
     */
    int calculatePriority(int v) {
        // set of shortcuts that would be added if node v would be contracted next.
        Collection<Shortcut> tmpShortcuts = findShortcuts(v);
        // from shortcuts we can compute the edgeDifference
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|        
        // meanDegree is used instead of outDegree+inDegree as if one edge is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        int degree = GraphUtility.count(g.getEdges(v));
        int edgeDifference = tmpShortcuts.size() - degree;

        // every edge has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdges = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        int originalEdges = 0;
        for (Shortcut sc : tmpShortcuts) {
            originalEdges += sc.originalEdges;
        }

        // number of already contracted neighbors of v
        int contractedNeighbors = 0;
        EdgeSkipIterator iter = g.getEdges(v);
        while (iter.next()) {
            if (iter.skippedNode() >= 0)
                contractedNeighbors++;
        }

        // according to the paper do a simple linear combination of the properties to get the priority
        return 2 * edgeDifference + 4 * originalEdges + contractedNeighbors;
    }

    static class EdgeLevelFilterCH extends EdgeLevelFilter {

        int skipNode;

        public EdgeLevelFilterCH(LevelGraph g) {
            super(g);
        }

        public EdgeLevelFilterCH setSkipNode(int skipNode) {
            this.skipNode = skipNode;
            return this;
        }

        @Override public boolean accept() {
            // ignore if it is skipNode or a node already contracted
            return skipNode != node() && graph.getLevel(node()) == 0;
        }
    }

    Collection<Shortcut> findShortcuts(int v) {
        // Do NOT use weight use distance! see prepareEdges where distance is overwritten by weight!
        List<NodeCH> goalNodes = new ArrayList<NodeCH>();
        shortcuts.clear();
        EdgeSkipIterator iter1 = g.getIncoming(v);
        while (iter1.next()) {
            int u = iter1.node();
            int lu = g.getLevel(u);
            if (lu != 0)
                continue;

            double v_u_weight = iter1.distance();

            // one-to-many shortest path
            goalNodes.clear();
            EdgeSkipIterator iter2 = g.getOutgoing(v);
            double maxWeight = 0;
            while (iter2.next()) {
                int w = iter2.node();
                int lw = g.getLevel(w);
                if (w == u || lw != 0)
                    continue;

                NodeCH n = new NodeCH();
                n.node = w;
                n.originalEdges = iter2.originalEdges();
                n.distance = v_u_weight + iter2.distance();
                goalNodes.add(n);

                if (maxWeight < n.distance)
                    maxWeight = n.distance;
            }

            // TODO instead of a weight-limit we could use a hop-limit 
            // and successively increasing it when mean-degree of graph increases
            algo = new OneToManyDijkstraCH(g).setFilter(edgeFilter.setSkipNode(v));
            algo.setLimit(maxWeight).calcPath(u, goalNodes);
            for (NodeCH n : goalNodes) {
                if (n.entry != null) {
                    Path p = algo.extractPath(n.entry);
                    if (p != null && p.weight() <= n.distance) {
                        // FOUND witness path => do not add shortcut
                        continue;
                    }
                }

                // FOUND shortcut but be sure that it is the only shortcut in the collection 
                // and also in the graph for u->w. If existing => update it
                long edge = u + n.node;
                Shortcut sc = shortcuts.get(edge);
                if (sc == null || sc.distance != n.distance) {
                    sc = new Shortcut(u, n.node, n.distance);
                    sc.originalEdges = iter1.originalEdges() + n.originalEdges;
                    shortcuts.put(edge, sc);

                    // determine if a shortcut already exists in the graph
                    EdgeSkipIterator tmpIter = g.getOutgoing(u);
                    while (tmpIter.next()) {
                        if (tmpIter.node() != n.node || tmpIter.skippedNode() < 0)
                            continue;

                        if (tmpIter.distance() > n.distance)
                            sc.update = true;
                    }
                } else {
                    // the shortcut already exists in the current collection (different direction)
                    // but has identical length so change the flags!
                    sc.flags = scBothDir;
                }
            }
        }
        return shortcuts.values();
    }

    public static class OneToManyDijkstraCH extends DijkstraSimple {

        EdgeLevelFilter filter;
        double limit;
        Collection<NodeCH> goals;

        public OneToManyDijkstraCH(Graph graph) {
            super(graph);
            setType(ShortestCalc.DEFAULT);
        }

        public OneToManyDijkstraCH setFilter(EdgeLevelFilter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        protected final EdgeIterator getNeighbors(int neighborNode) {
            return filter.doFilter(super.getNeighbors(neighborNode));
        }

        OneToManyDijkstraCH setLimit(double weight) {
            limit = weight;
            return this;
        }

        @Override public OneToManyDijkstraCH clear() {
            super.clear();
            return this;
        }

        @Override public Path calcPath(int from, int to) {
            throw new IllegalArgumentException("call the other calcPath instead");
        }

        Path calcPath(int from, Collection<NodeCH> goals) {
            this.goals = goals;
            return super.calcPath(from, -1);
        }

        @Override public boolean finished(EdgeEntry curr, int _ignoreTo) {
            if (curr.weight > limit)
                return true;

            int found = 0;
            for (NodeCH n : goals) {
                if (n.node == curr.node) {
                    n.entry = curr;
                    found++;
                } else if (n.entry != null) {
                    found++;
                }
            }
            return found == goals.size();
        }
    }

    /**
     * Introduces the necessary shortcuts for node v in the graph.
     */
    int addShortcuts(int v) {
        Collection<Shortcut> foundShortcuts = findShortcuts(v);
//        System.out.println("contract:" + refs[v] + ", scs:" + shortcuts);
        int newShorts = 0;
        for (Shortcut sc : foundShortcuts) {
            if (sc.update) {
                EdgeSkipIterator iter = g.getOutgoing(sc.from);
                while (iter.next()) {
                    if (iter.node() == sc.to && iter.distance() > sc.distance)
                        iter.distance(sc.distance);
                }
            } else {
                EdgeSkipIterator iter = g.shortcut(sc.from, sc.to, sc.distance, sc.flags, v);
                iter.originalEdges(sc.originalEdges);
                newShorts++;
            }
        }
        return newShorts;
    }

    public DijkstraBidirectionRef createDijkstraBi() {
        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g) {
            @Override public RoutingAlgorithm setType(WeightCalculation wc) {
                // ignore changing of type
                return this;
            }

            @Override protected PathBidirRef createPath() {
                WeightCalculation wc = new WeightCalculation() {
                    @Override
                    public double getWeight(EdgeIterator iter) {
                        return iter.distance() * CarStreetType.getSpeedPart(iter.flags());
                    }

                    @Override public double apply(double currDistToGoal) {
                        throw new UnsupportedOperationException();
                    }

                    @Override public double apply(double currDistToGoal, int flags) {
                        throw new UnsupportedOperationException();
                    }

                    @Override public String toString() {
                        return "INVERSE";
                    }
                };
                return new Path4Level(graph, wc);
            }
        };
        dijkstra.setEdgeFilter(new EdgeLevelFilter(g));
        return dijkstra;
    }
}
