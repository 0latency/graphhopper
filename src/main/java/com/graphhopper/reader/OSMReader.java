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
package com.graphhopper.reader;

import com.graphhopper.routing.util.AcceptStreet;
import com.graphhopper.routing.util.PrepareContractionHierarchies;
import com.graphhopper.routing.util.PrepareLongishPathShortcuts;
import com.graphhopper.routing.util.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.RoutingAlgorithmIntegrationTests;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.GraphStorageWrapper;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.Storage;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.StopWatch;
import gnu.trove.list.array.TIntArrayList;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class parses an OSM xml file and creates a graph from it. See run.sh on how to use it from
 * command line.
 *
 * @author Peter Karich,
 */
public class OSMReader {

    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        if (!args.get("config", "").isEmpty()) {
            args = CmdArgs.readFromConfig(args.get("config", ""));
            // overwrite from command line
            args.merge(CmdArgs.read(strs));
        }

        Graph g = osm2Graph(args);
        // only possible for smaller graphs as we need to have two graphs + an array laying around
        // g = GraphUtility.sortDFS(g, new RAMDirectory());
        RoutingAlgorithmIntegrationTests tests = new RoutingAlgorithmIntegrationTests(g);
        if (args.getBool("osmreader.test", false)) {
            tests.start();
        } else if (args.getBool("osmreader.runshortestpath", false)) {
            String algo = args.get("osmreader.algo", "dijkstra");
            int iters = args.getInt("osmreader.algoIterations", 50);
            //warmup
            tests.runShortestPathPerf(iters / 10, algo);
            tests.runShortestPathPerf(iters, algo);
        }
    }
    private int expectedLocs;
    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private int locations = 0;
    private int skippedLocations = 0;
    private int nextEdgeIndex = 0;
    private int skippedEdges = 0;
    private Storage storage;
    private TIntArrayList tmpLocs = new TIntArrayList(10);
    private Map<String, Object> properties = new HashMap<String, Object>();
    private DistanceCalc callback = new DistanceCalc();
    private AcceptStreet acceptStreets = new AcceptStreet(true, false, false, false);
    private boolean chShortcuts;
    private boolean simpleShortcuts;

    /**
     * Opens or creates a graph. The specified args need a property 'graph' (a folder) and if no
     * such folder exist it'll create a graph from the provided osm file (property 'osm'). A
     * property 'size' is used to preinstantiate a datastructure/graph to avoid over-memory
     * allocation or reallocation (default is 5mio)
     */
    public static Graph osm2Graph(final CmdArgs args) throws IOException {
        String graphLocation = args.get("osmreader.graph-location", "");
        if (Helper.isEmpty(graphLocation)) {
            String strOsm = args.get("osmreader.osm", "");
            if (Helper.isEmpty(strOsm))
                graphLocation = "graph-gh";
            else
                graphLocation = Helper.pruneFileEnd(strOsm) + "-gh";
        }

        int size = (int) args.getLong("osmreader.size", 5 * 1000 * 1000);
        Storage storage;
        String dataAccess = args.get("osmreader.dataaccess", "inmemory+save");
        Directory dir;
        if ("mmap".equalsIgnoreCase(dataAccess)) {
            dir = new MMapDirectory(graphLocation);
        } else {
            if ("inmemory+save".equalsIgnoreCase(dataAccess))
                dir = new RAMDirectory(graphLocation, true);
            else
                dir = new RAMDirectory(graphLocation, false);
        }

        if (args.getBool("osmreader.levelgraph", false))
            storage = new GraphStorageWrapper(new LevelGraphStorage(dir), size);
        else
            // necessary for simple or CH shortcuts
            storage = new GraphStorageWrapper(new GraphStorage(dir), size);
        return osm2Graph(new OSMReader(storage, size), args);
    }

    public static Graph osm2Graph(OSMReader osmReader, CmdArgs args) throws IOException {
        if (!osmReader.loadExisting()) {
            String strOsm = args.get("osmreader.osm", "");
            if (Helper.isEmpty(strOsm))
                throw new IllegalArgumentException("Graph not found and no OSM xml provided.");

            File osmXmlFile = new File(strOsm);
            if (!osmXmlFile.exists())
                throw new IllegalStateException("Your specified OSM file does not exist:" + strOsm);
            logger.info("size for osm2id-map is " + osmReader.getMaxLocs() + " - start creating graph from " + osmXmlFile);
            String type = args.get("osmreader.type", "CAR");
            osmReader.setAcceptStreet(new AcceptStreet(type.contains("CAR"),
                    type.contains("PUBLIC_TRANSPORT"),
                    type.contains("BIKE"), type.contains("FOOT")));

            osmReader.setSimpleShortcuts(args.getBool("osmreader.simpleShortcuts", false));
            osmReader.setCHShortcuts(args.getBool("osmreader.chShortcuts", false));
            osmReader.osm2Graph(osmXmlFile);
        }

        return osmReader.getGraph();
    }

    public OSMReader(String storageLocation, int size) {
        this(new GraphStorageWrapper(new GraphStorage(new RAMDirectory(storageLocation, true)), size), size);
    }

    public OSMReader(Storage storage, int size) {
        this.storage = storage;
        expectedLocs = size;
        logger.info("using " + storage.toString() + ", memory:" + Helper7.getBeanMemInfo());
    }

    private int getMaxLocs() {
        return expectedLocs;
    }

    public boolean loadExisting() {
        return storage.loadExisting();
    }

    private InputStream createInputStream(File file) throws IOException {
        FileInputStream fi = new FileInputStream(file);
        if (file.getAbsolutePath().endsWith(".gz"))
            return new GZIPInputStream(fi);
        else if (file.getAbsolutePath().endsWith(".zip"))
            return new ZipInputStream(fi);

        return fi;
    }

    public void osm2Graph(File osmXmlFile) throws IOException {
        // TODO instead of creating two separate input streams,
        // could we use PushbackInputStream(new FileInputStream(osmFile)); ???

        preprocessAcceptHighwaysOnly(createInputStream(osmXmlFile));
        writeOsm2Graph(createInputStream(osmXmlFile));
        cleanUp();
        optimize();
        flush();
    }

    public void optimize() {
        Graph g = storage.getGraph();
        if (g instanceof LevelGraph) {
            if (simpleShortcuts)
                new PrepareLongishPathShortcuts((LevelGraph) g).doWork();
            if (chShortcuts)
                new PrepareContractionHierarchies((LevelGraph) g).doWork();
        }
    }

    public void cleanUp() {
        Graph g = storage.getGraph();
        int prev = g.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(g);
        logger.info("start finding subnetworks");
        preparation.doWork();
        int n = g.getNodes();
        logger.info("nodes " + n + ", there were " + preparation.getSubNetworks() + " sub-networks. removed them => " + (prev - n)
                + " less nodes. Remaining subnetworks:" + preparation.findSubnetworks().size());
    }

    public void flush() {
        logger.info("flushing ...");
        storage.flush();
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    public void preprocessAcceptHighwaysOnly(InputStream osmXml) {
        if (osmXml == null)
            throw new IllegalStateException("Stream cannot be empty");

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(osmXml, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {
                if (++counter % 10000000 == 0) {
                    logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                            + " (" + skippedEdges + "), " + Helper.getMemInfo());
                }
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("way".equals(sReader.getLocalName())) {
                            boolean isHighway = isHighway(sReader);
                            if (isHighway && tmpLocs.size() > 1) {
                                int s = tmpLocs.size();
                                for (int index = 0; index < s; index++) {
                                    storage.setHasHighways(tmpLocs.get(index), true);
                                }
                            }
                        }
                        break;
                }
            }
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        } finally {
            Helper7.close(sReader);
        }
    }

    /**
     * Creates the edges and nodes files from the specified inputstream (osm xml file).
     */
    public void writeOsm2Graph(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");
        logger.info("init storage with " + storage.getNodes() + " nodes");
        storage.createNew();
        logger.info("starting 2nd parsing");
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        int wayStart = -1;
        StopWatch sw = new StopWatch();
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName())) {
                            processNode(sReader);
                            if (++counter % 10000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        } else if ("way".equals(sReader.getLocalName())) {
                            if (wayStart < 0) {
                                logger.info("parsing ways");
                                wayStart = counter;
                                sw.start();
                            }
                            processHighway(sReader);
                            if (counter - wayStart == 10000 && sw.stop().getSeconds() > 1) {
                                logger.warn("Something is wrong! Processing ways takes too long! "
                                        + sw.getSeconds() + "sec for only " + (counter - wayStart) + " docs");
                            }
                            if (counter++ % 1000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        }

                        break;
                }
            }
            // logger.info("storage nodes:" + storage.getNodes() + " vs. graph nodes:" + storage.getGraph().getNodes());
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't process file", ex);
        } finally {
            Helper7.close(sReader);
        }
    }

    public void processNode(XMLStreamReader sReader) throws XMLStreamException {
        int osmId;
        boolean hasHighways;
        try {
            osmId = Integer.parseInt(sReader.getAttributeValue(null, "id"));
            hasHighways = storage.hasHighways(osmId);
        } catch (Exception ex) {
            logger.error("cannot get id from xml node:" + sReader.getAttributeValue(null, "id"), ex);
            return;
        }

        double lat = -1;
        double lon = -1;
        try {
            lat = Double.parseDouble(sReader.getAttributeValue(null, "lat"));
            lon = Double.parseDouble(sReader.getAttributeValue(null, "lon"));
            if (isInBounds(lat, lon)) {
                if (hasHighways) {
                    storage.addNode(osmId, lat, lon);
                    locations++;
                }
            } else {
                // remove the osmId if not in bounds to avoid trouble when processing the highway
                if (hasHighways)
                    storage.setHasHighways(osmId, false);

                skippedLocations++;
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle lon/lat of node " + osmId + ": " + lat + "," + lon, ex);
        }
    }

    public boolean isInBounds(double lat, double lon) {
        return true;
    }

    boolean parseWay(TIntArrayList tmpLocs, Map<String, Object> properties, XMLStreamReader sReader)
            throws XMLStreamException {

        boolean handled = false;
        tmpLocs.clear();
        properties.clear();
        for (int tmpE = sReader.nextTag(); tmpE != XMLStreamConstants.END_ELEMENT;
                tmpE = sReader.nextTag()) {
            if (tmpE == XMLStreamConstants.START_ELEMENT) {
                if ("nd".equals(sReader.getLocalName())) {
                    String ref = sReader.getAttributeValue(null, "ref");
                    try {
                        tmpLocs.add(Integer.parseInt(ref));
                    } catch (Exception ex) {
                        logger.error("cannot get ref from way. ref:" + ref, ex);
                    }
                } else if ("tag".equals(sReader.getLocalName())) {
                    String key = sReader.getAttributeValue(null, "k");
                    if (key != null && !key.isEmpty()) {
                        String val = sReader.getAttributeValue(null, "v");
                        if ("highway".equals(key)) {
                            handled = acceptStreets.handleWay(properties, val);
//                            if ("proposed".equals(val) || "preproposed".equals(val)
//                                    || "platform".equals(val) || "raceway".equals(val)
//                                    || "bus_stop".equals(val) || "bridleway".equals(val)
//                                    || "construction".equals(val) || "no".equals(val) || "centre_line".equals(val))
//                                // ignore
//                                val = val;
//                            else
//                                logger.warn("unknown highway type:" + val);
                        } else if ("oneway".equals(key)) {
                            if ("yes".equals(val) || "true".equals(val) || "1".equals(val))
                                properties.put("oneway", "yes");
                        } else if ("junction".equals(key)) {
                            // abzweigung
                            if ("roundabout".equals(val))
                                properties.put("oneway", "yes");
                        }
                    }
                }

                sReader.next();
            }
        }
        return handled;
    }

    public boolean isHighway(XMLStreamReader sReader) throws XMLStreamException {
        return parseWay(tmpLocs, properties, sReader);
    }

    public void processHighway(XMLStreamReader sReader) throws XMLStreamException {
        if (isHighway(sReader) && tmpLocs.size() > 1) {
            int prevOsmId = tmpLocs.get(0);
            int l = tmpLocs.size();
            int flags = acceptStreets.toFlags(properties);
            for (int index = 1; index < l; index++) {
                int currOsmId = tmpLocs.get(index);
                boolean ret = storage.addEdge(prevOsmId, currOsmId, flags, callback);
                if (ret)
                    nextEdgeIndex++;
                else
                    skippedEdges++;
                prevOsmId = currOsmId;
            }
        }
    }

    public Graph getGraph() {
        return storage.getGraph();
    }

    private void stats() {
        logger.info("Stats");

//        printSorted(countMap.entrySet());
//        printSorted(highwayMap.entrySet());
        storage.stats();
    }

    private void printSorted(Set<Entry<String, Integer>> entrySet) {
        List<Entry<String, Integer>> list = new ArrayList<Entry<String, Integer>>(entrySet);
        Collections.sort(list, new Comparator<Entry<String, Integer>>() {
            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                return o1.getValue() - o2.getValue();
            }
        });
        for (Entry<String, Integer> e : list) {
            logger.info(e.getKey() + "\t -> " + e.getValue());
        }
        logger.info("---");
    }

    public OSMReader setAcceptStreet(AcceptStreet acceptStr) {
        this.acceptStreets = acceptStr;
        return this;
    }

    public OSMReader setCHShortcuts(boolean bool) {
        chShortcuts = bool;
        return this;
    }

    public OSMReader setSimpleShortcuts(boolean bool) {
        simpleShortcuts = bool;
        return this;
    }
}
