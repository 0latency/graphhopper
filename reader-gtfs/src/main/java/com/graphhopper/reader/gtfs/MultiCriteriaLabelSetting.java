/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.gtfs;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Implements a Multi-Criteria Label Setting (MLS) path finding algorithm
 * with the criteria earliest arrival time and number of transfers.
 * <p>
 *
 * @author Michael Zilske
 * @author Peter Karich
 */
class MultiCriteriaLabelSetting {
    private final Graph graph;
    private final PtFlagEncoder flagEncoder;
    private final Weighting weighting;
    private final SetMultimap<Integer, SPTEntry> fromMap;
    private final PriorityQueue<SPTEntry> fromHeap;
    private final int maxVisitedNodes;
    private long rangeQueryEndTime;
    private GtfsStorage gtfsStorage;
    private int visitedNodes;

    MultiCriteriaLabelSetting(Graph graph, Weighting weighting, int maxVisitedNodes, GtfsStorage gtfsStorage) {
        this.graph = graph;
        this.weighting = weighting;
        this.flagEncoder = (PtFlagEncoder) weighting.getFlagEncoder();
        this.maxVisitedNodes = maxVisitedNodes;
        this.gtfsStorage = gtfsStorage;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        fromHeap = new PriorityQueue<>(size/*, new SPTEntryComparator(gtfsStorage)*/);
        fromMap = HashMultimap.create();
    }

    Set<SPTEntry> calcPaths(int from, Set<Integer> to, long startTime, long rangeQueryEndTime) {
        this.rangeQueryEndTime = rangeQueryEndTime;
        Set<SPTEntry> targetLabels = new HashSet<>();
        SPTEntry label = new SPTEntry(EdgeIterator.NO_EDGE, from, startTime, 0, Long.MAX_VALUE);
        fromMap.put(from, label);
        if (to.contains(from)) {
            targetLabels.add(label);
        }
        EdgeExplorer explorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, false, true));
        while (true) {
            visitedNodes++;
            if (maxVisitedNodes < visitedNodes)
                break;

            int startNode = label.adjNode;
            EdgeIterator iter = explorer.setBaseNode(startNode);
            boolean foundEnteredTimeExpandedNetworkEdge = false;
            while (iter.next()) {
                if (iter.getEdge() == label.edge)
                    continue;

                GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(iter.getFlags());
                if (edgeType == GtfsStorage.EdgeType.BOARD_EDGE) {
                    int trafficDay = (int) (label.weight) / (24 * 60 * 60);
                    if (!isValidOn(iter, trafficDay)) {
                        continue;
                    }
                } else if (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                    if ((int) (label.weight) % (24 * 60 * 60) > flagEncoder.getTime(iter.getFlags())) {
                        continue;
                    } else {
                        if (foundEnteredTimeExpandedNetworkEdge) {
                            continue;
                        } else {
                            foundEnteredTimeExpandedNetworkEdge = true;
                        }
                    }
                } else if (edgeType == GtfsStorage.EdgeType.STOP_EXIT_NODE_MARKER_EDGE
                        || edgeType == GtfsStorage.EdgeType.STOP_NODE_MARKER_EDGE) {
                    continue;
                }

                double tmpWeight;
                int tmpNTransfers = label.nTransfers;
                long tmpFirstPtDepartureTime = label.firstPtDepartureTime;
                if (weighting instanceof TimeDependentWeighting) {
                    tmpWeight = ((TimeDependentWeighting) weighting).calcWeight(iter, false, label.edge, label.weight) + label.weight;
                    tmpNTransfers += ((TimeDependentWeighting) weighting).calcNTransfers(iter);
                } else {
                    tmpWeight = weighting.calcWeight(iter, false, label.edge) + label.weight;
                }
                if (edgeType == GtfsStorage.EdgeType.BOARD_EDGE && tmpFirstPtDepartureTime == Long.MAX_VALUE) {
                    tmpFirstPtDepartureTime = (long) tmpWeight;
                }
                if (Double.isInfinite(tmpWeight))
                    continue;

                Set<SPTEntry> sptEntries = fromMap.get(iter.getAdjNode());
                SPTEntry nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight, tmpNTransfers, tmpFirstPtDepartureTime);
                nEdge.parent = label;
                if (improves(nEdge, sptEntries) && improves(nEdge, targetLabels)) {
                    removeDominated(nEdge, sptEntries);
                    if (to.contains(iter.getAdjNode())) {
                        removeDominated(nEdge, targetLabels);
                    }
                    fromMap.put(iter.getAdjNode(), nEdge);
                    if (to.contains(iter.getAdjNode())) {
                        targetLabels.add(nEdge);
                    }
                    fromHeap.add(nEdge);
                }
            }

            if (fromHeap.isEmpty())
                break;

            label = fromHeap.poll();
            if (label == null)
                throw new AssertionError("Empty edge cannot happen");
        }
        return targetLabels;
    }

    private boolean isValidOn(EdgeIterator iter, int trafficDay) {
        return gtfsStorage.getReverseOperatingDayPatterns().get((int) flagEncoder.getTime(iter.getFlags())).get(trafficDay);
    }

    private boolean improves(SPTEntry me, Set<SPTEntry> sptEntries) {
        for (SPTEntry they : sptEntries) {
            if (they.nTransfers <= me.nTransfers && they.weight <= me.weight && (they.firstPtDepartureTime >= me.firstPtDepartureTime || me.firstPtDepartureTime > rangeQueryEndTime)) {
                return false;
            }
        }
        return true;
    }

    private void removeDominated(SPTEntry me, Set<SPTEntry> sptEntries) {
        for (Iterator<SPTEntry> iterator = sptEntries.iterator(); iterator.hasNext();) {
            SPTEntry sptEntry = iterator.next();
            if (dominates(me, sptEntry)) {
                fromHeap.remove(sptEntry);
                iterator.remove();
            }
        }
    }

    private boolean isNotDominatedBy(SPTEntry me, Set<SPTEntry> sptEntries) {
        for (SPTEntry they : sptEntries) {
            if (dominates(they, me)) {
                return false;
            }
        }
        return true;
    }

    private boolean dominates(SPTEntry me, SPTEntry they) {
        if (me.weight > they.weight) {
            return false;
        }
        if (me.nTransfers > they.nTransfers) {
            return false;
        }
        if (me.firstPtDepartureTime < they.firstPtDepartureTime) {
            return false;
        }
        if (me.weight < they.weight) {
            return true;
        }
        if (me.nTransfers < they.nTransfers) {
            return true;
        }
        if (me.firstPtDepartureTime > they.firstPtDepartureTime) {
            return true;
        }
        return false;
    }

    int getVisitedNodes() {
        return visitedNodes;
    }

}
