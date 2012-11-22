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
package com.graphhopper.storage;

import com.graphhopper.coll.CompressedArray;
import com.graphhopper.coll.OSMIDSegmentedMap;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.shapes.CoordTrig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class DefaultStorage implements Storage {

    protected static final int FILLED = -2;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected Graph g;
    // protected OSMIDMap osmIdToIndexMap;
    protected OSMIDSegmentedMap osmIdToIndexMap;
    protected CompressedArray latLonArray;
    private int expectedNodes;
    private int counter = 0;
    private int zeroCounter = 0;

    public DefaultStorage(int expectedNodes) {
        this.expectedNodes = expectedNodes;
        osmIdToIndexMap = new OSMIDSegmentedMap(expectedNodes, 100);
        latLonArray = new CompressedArray();
        // osmIdToIndexMap = new OSMIDMap(expectedNodes);
    }

    @Override public boolean loadExisting() {
        return false;
    }

    @Override public void createNew() {
    }

    @Override public int getExpectedNodes() {
        return expectedNodes;
    }

    @Override
    public boolean addNode(long osmId, double lat, double lon) {
        // map.get returns the required index for the latLon array. 
        // TODO later we need the index also for the way/node name
        osmIdToIndexMap.write(osmId);
        latLonArray.write(lat, lon);
        return true;
    }

    @Override
    public boolean addEdge(long nodeIdFrom, long nodeIdTo, int flags, DistanceCalc callback) {
        int fromIndex = (int) osmIdToIndexMap.get(nodeIdFrom);
        int toIndex = (int) osmIdToIndexMap.get(nodeIdTo);
        if (fromIndex == osmIdToIndexMap.getNoEntryValue() || toIndex == osmIdToIndexMap.getNoEntryValue())
            return false;

        try {
            CoordTrig from = latLonArray.get(fromIndex);
            CoordTrig to = latLonArray.get(toIndex);
            if (from == null || to == null)
                // probably out of bounds
                return false;

            g.setNode(fromIndex, from.lat, from.lon);
            g.setNode(toIndex, to.lat, to.lon);
            double dist = callback.calcDistKm(from.lat, from.lon, to.lat, to.lon);
            if (dist == 0) {
                // As investigation shows often two paths should have crossed via one identical point 
                // but end up in two very close points. later this will be removed/fixed while 
                // removing short edges where one node is of degree 2
                zeroCounter++;
                dist = 0.0001;
            } else if (dist < 0) {
                logger.info(counter + " - distances negative. " + fromIndex + " (" + from.lat + ", " + from.lon + ")->"
                        + toIndex + "(" + to.lat + ", " + to.lon + ") :" + dist);
                return false;
            }

            EdgeIterator iter = GraphUtility.until(g.getOutgoing(fromIndex), toIndex);
            if (!iter.isEmpty()) {
                if (flags == iter.flags() && dist > iter.distance()) {
                    // silently skip if exactly the same way and the new one would be longer
//                    return true;
                }
//                else logger.warn("longer edge already exists " + fromIndex + "->" + toIndex + "!? "
//                            + "existing: " + iter.distance() + "|" + BitUtil.toBitString(iter.flags(), 8)
//                            + " new:" + dist + "|" + BitUtil.toBitString(flags, 8));
            }

            g.edge(fromIndex, toIndex, dist, flags);
            return true;
        } catch (Exception ex) {
            throw new RuntimeException("Problem to add edge! with node ids " + fromIndex + "->" + toIndex
                    + " vs. osm ids:" + nodeIdFrom + "->" + nodeIdTo, ex);
        }
    }

    @Override
    public void close() {
        flush();
    }

    @Override public Graph getGraph() {
        return g;
    }

    @Override public void stats() {
    }

    @Override
    public void flush() {
        logger.info("Found " + zeroCounter + " zero and " + counter + " negative distances.");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public void freeOSMIDMap() {
        osmIdToIndexMap = null;
    }

    public void flushLatLonArray() {
        latLonArray.flush();
    }
}
