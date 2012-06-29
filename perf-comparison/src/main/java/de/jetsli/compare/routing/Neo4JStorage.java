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
package de.jetsli.compare.routing;

import de.jetsli.graph.storage.DefaultStorage;
import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.storage.DistEntry;
import de.jetsli.graph.util.Helper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Neo4j is great and uses very few RAM when importing. It is also fast (2 mio nodes / min)
 *
 * But it (wrongly?) uses lucene necessray for node lookup which makes it slow (500k nodes / min)
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Neo4JStorage extends DefaultStorage {

    private static final String ID = "_id";
    private static final String DISTANCE = "distance";
    private static final Logger logger = LoggerFactory.getLogger(Neo4JStorage.class);

    enum MyRelations implements RelationshipType {

        WAY
    }
    private boolean temporary;
    private GraphDatabaseService graphDb;
    private String storeDir;
    int index;
    Transaction ta;

    public Neo4JStorage() {
        this(null, 5000000);
    }

    public Neo4JStorage(String storeDir, int size) {
        super(size);
        if (storeDir != null) {
            temporary = false;
            this.storeDir = storeDir;
        } else {
            temporary = true;
            this.storeDir = "neo4j." + new Random().nextLong() + ".db";
        }
    }

    @Override
    public boolean loadExisting() {
        return init(false);
    }

    @Override
    public void createNew() {
        init(true);
    }

    public boolean init(boolean forceCreate) {
        try {
            graphDb = new EmbeddedGraphDatabase(storeDir);
            if (!temporary)
                Runtime.getRuntime().addShutdownHook(new Thread() {

                    @Override
                    public void run() {
                        try {
                            close();
                        } catch (Exception ex) {
                            logger.error("problem while closing neo4j graph db", ex);
                        }
                    }
                });
            return true;
        } catch (Exception ex) {
            logger.error("problem while initialization", ex);
            return false;
        }
    }

//    @Override
//    public boolean addNode(int osmId, double lat, double lon) {
//        ensureTA();
//
//        Node n = graphDb.createNode();
//        // id necessary when grabbing relation info
//        n.setProperty(ID, osmId);
//        n.setProperty("lat", lat);
//        n.setProperty("lon", lon);
//
//        // id also necessary when doing look up
////        locIndex.add(n, ID, osmId);
//        return true;
//    }
//    @Override
//    public boolean addEdge(int nodeIdFrom, int nodeIdTo, boolean reverse, CalcDistance callback) {
//        ensureTA();
//        Node from = locIndex.get(ID, nodeIdFrom).getSingle();
//        Node to = locIndex.get(ID, nodeIdTo).getSingle();
//        Relationship r = from.createRelationshipTo(to, MyRelations.WAY);
//        r.setProperty(DISTANCE, callback.calcDistKm(
//                (Double) from.getProperty("lat"), (Double) from.getProperty("lon"),
//                (Double) to.getProperty("lat"), (Double) to.getProperty("lon")));
//        return true;
//    }
//    public List<DistEntry> getOutgoing(int node) {
//        Node n = locIndex.get(ID, node).getSingle();
//        ArrayList<DistEntry> list = new ArrayList<DistEntry>(2);
//        for (Relationship rs : n.getRelationships(MyRelations.WAY)) {
//            list.add(new DistEntry((Integer) rs.getEndNode().getProperty(ID), (Double) rs.getProperty(DISTANCE)));
//        }
//
//        return list;
//    }
    @Override
    public void close() {
        graphDb.shutdown();
        if (temporary)
            Helper.deleteDir(new File(storeDir));
    }

    private void ensureTA() {
        if (index++ % 20000 == 0) {
            if (ta != null) {
                ta.success();
                ta.finish();
            }

            ta = graphDb.beginTx();
        }
    }
}
