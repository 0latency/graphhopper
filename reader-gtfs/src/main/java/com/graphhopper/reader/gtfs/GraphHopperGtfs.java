package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.*;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.graphhopper.reader.gtfs.Label.reverseEdges;

public final class GraphHopperGtfs implements GraphHopperAPI {

    public static final String EARLIEST_DEPARTURE_TIME_HINT = "earliestDepartureTime";
    public static final String RANGE_QUERY_END_TIME = "rangeQueryEndTime";
    public static final String ARRIVE_BY = "arriveBy";
    public static final String IGNORE_TRANSFERS = "ignoreTransfers";

    private final TranslationMap translationMap;
    private final EncodingManager encodingManager;

    private GraphHopperStorage graphHopperStorage;
    private LocationIndex locationIndex;
    private GtfsStorage gtfsStorage;

    public GraphHopperGtfs(EncodingManager encodingManager, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        this.encodingManager = encodingManager;
        this.translationMap = translationMap;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
    }

    public static GraphHopperGtfs createGraphHopperGtfs(String graphHopperFolder, String gtfsFile, boolean createWalkNetwork) {
        EncodingManager encodingManager = createEncodingManager();

        if (Helper.isEmpty(graphHopperFolder))
            throw new IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before");

        if (graphHopperFolder.endsWith("-gh")) {
            // do nothing
        } else if (graphHopperFolder.endsWith(".osm") || graphHopperFolder.endsWith(".xml")) {
            throw new IllegalArgumentException("GraphHopperLocation cannot be the OSM file. Instead you need to use importOrLoad");
        } else if (!graphHopperFolder.contains(".")) {
            if (new File(graphHopperFolder + "-gh").exists())
                graphHopperFolder += "-gh";
        } else {
            File compressed = new File(graphHopperFolder + ".ghz");
            if (compressed.exists() && !compressed.isDirectory()) {
                try {
                    new Unzipper().unzip(compressed.getAbsolutePath(), graphHopperFolder, false);
                } catch (IOException ex) {
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath()
                            + " to " + graphHopperFolder, ex);
                }
            }
        }

        GtfsStorage gtfsStorage = createGtfsStorage();

        GHDirectory directory = createGHDirectory(graphHopperFolder);
        GraphHopperStorage graphHopperStorage = createOrLoad(directory, encodingManager, gtfsStorage, createWalkNetwork, Collections.singleton(gtfsFile), Collections.emptyList());
        LocationIndex locationIndex = createOrLoadIndex(directory, graphHopperStorage);

        return new GraphHopperGtfs(encodingManager, createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage);
    }

    public static GtfsStorage createGtfsStorage() {
        return new GtfsStorage();
    }

    public static GHDirectory createGHDirectory(String graphHopperFolder) {
        return new GHDirectory(graphHopperFolder, DAType.RAM_STORE);
    }

    public static TranslationMap createTranslationMap() {
        return new TranslationMap().doImport();
    }

    public static EncodingManager createEncodingManager() {
        return new EncodingManager(Arrays.asList(new PtFlagEncoder()), 8);
    }

    public static GraphHopperStorage createOrLoad(GHDirectory directory, EncodingManager encodingManager, GtfsStorage gtfsStorage, boolean createWalkNetwork, Collection<String> gtfsFiles, Collection<String> osmFiles) {
        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(directory, encodingManager, false, gtfsStorage);
        if (!new File(directory.getLocation()).exists()) {
            graphHopperStorage.create(1000);
            for (String osmFile : osmFiles) {
                OSMReader osmReader = new OSMReader(graphHopperStorage);
                osmReader.setEncodingManager(encodingManager);
                osmReader.setFile(new File(osmFile));
                osmReader.setDontCreateStorage(true);
                try {
                    osmReader.readGraph();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            List<GTFSFeed> feeds = gtfsFiles.parallelStream()
                    .map(filename -> GTFSFeed.fromFile(new File(filename).getPath()))
                    .collect(Collectors.toList());
            if (createWalkNetwork) {
                FakeWalkNetworkBuilder.buildWalkNetwork(feeds, graphHopperStorage, (PtFlagEncoder) encodingManager.getEncoder("pt"), Helper.DIST_EARTH);
            }
            LocationIndex walkNetworkIndex;
            if (graphHopperStorage.getNodes() > 0 ) {
                walkNetworkIndex = new LocationIndexTree(graphHopperStorage, new RAMDirectory()).prepareIndex();
            } else {
                walkNetworkIndex = new EmptyLocationIndex();
            }
            for (GTFSFeed feed : feeds) {
                new GtfsReader(feed, graphHopperStorage, walkNetworkIndex).readGraph();
            }
            graphHopperStorage.flush();
        } else {
            graphHopperStorage.loadExisting();
        }
        return graphHopperStorage;
    }


    public static LocationIndex createOrLoadIndex(GHDirectory directory, GraphHopperStorage graphHopperStorage) {
        LocationIndex locationIndex = new LocationIndexTree(graphHopperStorage, directory);
        if (!locationIndex.loadExisting()) {
            locationIndex.prepareIndex();
        }
        return locationIndex;
    }

    public boolean load(String graphHopperFolder) {
        throw new IllegalStateException("We are always loaded, or we wouldn't exist.");
    }

    @Override
    public GHResponse route(GHRequest request) {
        final int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, Integer.MAX_VALUE);
        final long requestedTimeOfDay = request.getHints().getInt(EARLIEST_DEPARTURE_TIME_HINT, 0) % (24 * 60 * 60);
        final long requestedDay = request.getHints().getInt(EARLIEST_DEPARTURE_TIME_HINT, 0) / (24 * 60 * 60);
        final long initialTime = requestedTimeOfDay + requestedDay * (24 * 60 * 60);
        final long rangeQueryEndTime = request.getHints().getLong(RANGE_QUERY_END_TIME, initialTime);
        final boolean arriveBy = request.getHints().getBool(ARRIVE_BY, false);
        final boolean ignoreTransfers = request.getHints().getBool(IGNORE_TRANSFERS, false);

        GHResponse response = new GHResponse();

        if (graphHopperStorage == null)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");

        if (graphHopperStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");

        PtFlagEncoder encoder = (PtFlagEncoder) encodingManager.getEncoder("pt");

        if (request.getPoints().size() != 2) {
            throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + request.getPoints().size());
        }

        final GHPoint enter = request.getPoints().get(0);
        final GHPoint exit = request.getPoints().get(1);


        Locale locale = request.getLocale();
        Translation tr = translationMap.getWithFallBack(locale);
        StopWatch stopWatch = new StopWatch().start();

        EdgeFilter enterFilter = new EverythingButPt(encoder);
        EdgeFilter exitFilter = new EverythingButPt(encoder);

        List<QueryResult> queryResults = new ArrayList<>();

        QueryResult source = locationIndex.findClosest(enter.lat, enter.lon, enterFilter);
        if (!source.isValid()) {
            response.addError(new PointNotFoundException("Cannot find entry point: " + enter, 0));
            return response;
        }
        queryResults.add(source);

        QueryResult dest = locationIndex.findClosest(exit.lat, exit.lon, exitFilter);
        if (!dest.isValid()) {
            response.addError(new PointNotFoundException("Cannot find exit point: " + exit, 0));
            return response;
        }
        queryResults.add(dest);

        QueryGraph queryGraph = new QueryGraph(graphHopperStorage);
        queryGraph.lookup(queryResults);

        int startNode;
        int destNode;
        if (arriveBy) {
            startNode = dest.getClosestNode();
            destNode = source.getClosestNode();
        } else {
            startNode = source.getClosestNode();
            destNode = dest.getClosestNode();
        }

        ArrayList<Integer> toNodes = new ArrayList<>();
        toNodes.add(destNode);

        response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

        long visitedNodesSum = 0L;

        stopWatch = new StopWatch().start();

        PtTravelTimeWeighting weighting = createPtTravelTimeWeighting(encoder, arriveBy, ignoreTransfers);

        GraphExplorer explorer;
        if (arriveBy) {
            explorer = new GraphExplorer(queryGraph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false)), encoder, gtfsStorage, true);
        } else {
            explorer = new GraphExplorer(queryGraph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true)), encoder, gtfsStorage, false);
        }

        MultiCriteriaLabelSetting router;
        if (arriveBy) {
           router = new MultiCriteriaLabelSetting(queryGraph, weighting, maxVisitedNodesForRequest, explorer, true);
        } else {
           router = new MultiCriteriaLabelSetting(queryGraph, weighting, maxVisitedNodesForRequest, explorer, false);
        }

        String debug = ", algoInit:" + stopWatch.stop().getSeconds() + "s";

        stopWatch = new StopWatch().start();
        Set<Label> solutions = router.calcPaths(startNode, new HashSet(toNodes), initialTime, rangeQueryEndTime);
        debug += ", routing:" + stopWatch.stop().getSeconds() + "s";

        response.addDebugInfo(debug);

        if (router.getVisitedNodes() >= maxVisitedNodesForRequest)
            throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + maxVisitedNodesForRequest);

        visitedNodesSum += router.getVisitedNodes();

        response.getHints().put("visited_nodes.sum", visitedNodesSum);
        response.getHints().put("visited_nodes.average", (float) visitedNodesSum);

        for (Label solution : solutions) {

            List<EdgeIteratorState> edges = new ArrayList<>();
            if (arriveBy) {
                reverseEdges(solution, queryGraph)
                        .forEach(edge -> edges.add(edge.detach(false)));
            } else {
                reverseEdges(solution, queryGraph)
                        .forEach(edge -> edges.add(edge.detach(true)));
                Collections.reverse(edges);
            }

            PathWrapper path = new PathWrapper();
            PointList waypoints = new PointList(queryResults.size(), true);
            for (QueryResult qr : queryResults) {
                waypoints.add(qr.getSnappedPoint());
            }
            path.setWaypoints(waypoints);

            List<List<EdgeIteratorState>> partitions = new ArrayList<>();
            for (EdgeIteratorState edge : edges) {
                if (partitions.isEmpty() || encoder.getEdgeType(edge.getFlags()) == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || encoder.getEdgeType(partitions.get(partitions.size()-1).get(partitions.get(partitions.size()-1).size()-1).getFlags()) == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                    partitions.add(new ArrayList<>());
                }
                partitions.get(partitions.size()-1).add(edge);
            }

            path.getLegs().addAll(partitions.stream().flatMap(partition -> legs(partition, queryGraph, encoder, weighting, tr).stream()).collect(Collectors.toList()));

            InstructionList instructions = new InstructionList(tr);
            for (Trip.Leg leg : path.getLegs()) {
                if (leg instanceof Trip.WalkLeg) {
                    for (Instruction instruction : ((Trip.WalkLeg) leg).instructions.subList(0, ((Trip.WalkLeg) leg).instructions.size()-1)) {
                        instructions.add(instruction);
                    }
                } else if (leg instanceof Trip.PtLeg) {
                    PointList pl = new PointList();
                    pl.add(((Trip.PtLeg) leg).boardStop.geometry.getY(), ((Trip.PtLeg) leg).boardStop.geometry.getX());
                    for (Trip.Stop stop : ((Trip.PtLeg) leg).stops) {
                        pl.add(stop.geometry.getY(), stop.geometry.getX());
                    }
                    instructions.add(new Instruction(Instruction.PT_START_TRIP, ((Trip.PtLeg) leg).trip_headsign, InstructionAnnotation.EMPTY, pl));
                }
            }

            path.setInstructions(instructions);
            PointList pointsList = new PointList();
            for (Instruction instruction : path.getInstructions()) {
                pointsList.add(instruction.getPoints());
            }
            path.setPoints(pointsList);
            path.setRouteWeight(solution.currentTime);
            path.setDistance(path.getLegs().stream().mapToDouble(Trip.Leg::getDistance).sum());
            path.setTime(solution.currentTime * 1000);
            path.setFirstPtLegDeparture(solution.firstPtDepartureTime);
            path.setNumChanges((int) path.getLegs().stream().filter(l->l instanceof Trip.PtLeg).count() - 1);
            response.add(path);
        }
        if (response.getAll().isEmpty()) {
            response.addError(new RuntimeException("No route found"));
        } else {
            if (arriveBy) {
                Collections.sort(response.getAll(), (p1, p2) -> -Double.compare(p1.getRouteWeight(), p2.getRouteWeight()));
            } else {
                Collections.sort(response.getAll(), (p1, p2) -> Double.compare(p1.getRouteWeight(), p2.getRouteWeight()));
            }
        }
        return response;
    }

    private static PtTravelTimeWeighting createPtTravelTimeWeighting(PtFlagEncoder encoder, boolean arriveBy, boolean ignoreTransfers) {
        PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(encoder);
        if (arriveBy) {
            weighting = weighting.reverse();
        }
        if (ignoreTransfers) {
            weighting = weighting.ignoringNumberOfTransfers();
        }
        return weighting;
    }

    // Ugly: What we are writing here is a parser. We are parsing a string of edges
    // into a hierarchical trip.
    // One could argue that one should never write a parser
    // by hand, because it is always ugly, but use a parser library.
    // The code would then read like a specification of what paths through the graph mean.
    private List<Trip.Leg> legs(List<EdgeIteratorState> path, Graph graph, PtFlagEncoder encoder, Weighting weighting, Translation tr) {
        GeometryFactory geometryFactory = new GeometryFactory();
        if (GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK == encoder.getEdgeType(path.get(0).getFlags())) {
            Trip.Stop stop = stopFromHopEdge(geometryFactory, path.get(0));
            List<Trip.Leg> result = new ArrayList<>();
            LocalDateTime time = gtfsStorage.getStartDate().atStartOfDay().plusSeconds(encoder.getTime(path.get(0).getFlags()));
            LocalDateTime boardTime = null;
            List<EdgeIteratorState> partition = null;
            Trip.Stop boardStop = null;
            for (int i=1; i<path.size(); i++) {
                EdgeIteratorState edge = path.get(i);
                GtfsStorage.EdgeType edgeType = encoder.getEdgeType(edge.getFlags());
                if (edgeType == GtfsStorage.EdgeType.BOARD) {
                    if (boardTime != null) {
                        result.add(createPtLeg(geometryFactory, encoder, boardStop, partition, Date.from(boardTime.atZone(ZoneId.systemDefault()).toInstant())));
                    }
                    boardTime = time;
                    boardStop = stop;
                    partition = new ArrayList<>();
                }
                if (partition != null) {
                    partition.add(edge);
                }
                if (EnumSet.of(GtfsStorage.EdgeType.TRANSFER, GtfsStorage.EdgeType.HOP, GtfsStorage.EdgeType.TIME_PASSES).contains(edgeType)) {
                    time = time.plusSeconds(encoder.getTime(edge.getFlags()));
                }
                if (edgeType == GtfsStorage.EdgeType.HOP) {
                    stop = stopFromHopEdge(geometryFactory, edge);
                }
            }
            if (boardTime != null) {
                result.add(createPtLeg(geometryFactory, encoder, boardStop, partition, Date.from(boardTime.atZone(ZoneId.systemDefault()).toInstant())));
            }
            return result;
        } else {
            InstructionList instructions = new InstructionList(tr);
            InstructionsFromEdges instructionsFromEdges = new InstructionsFromEdges(path.get(0).getBaseNode(), graph, weighting, weighting.getFlagEncoder(), graph.getNodeAccess(), tr, instructions);
            int prevEdgeId = -1;
            for (int i=0; i<path.size(); i++) {
                EdgeIteratorState edge = path.get(i);
                instructionsFromEdges.next(edge, i, prevEdgeId);
                prevEdgeId = edge.getEdge();
            }
            instructionsFromEdges.finish();
            return Collections.singletonList(new Trip.WalkLeg(
                    "Walk",
                    path,
                    lineStringFromEdges(geometryFactory, path),
                    path.stream().mapToDouble(EdgeIteratorState::getDistance).sum(),
                    StreamSupport.stream(instructions.spliterator(), false).collect(Collectors.toList())));
        }
    }

    static Trip.PtLeg createPtLeg(GeometryFactory geometryFactory, PtFlagEncoder encoder, Trip.Stop stop, List<EdgeIteratorState> edges, Date departureTime) {
        Geometry lineString = lineStringFromEdges(geometryFactory, edges);
        List<Trip.Stop> stops = edges.stream().filter(e -> EnumSet.of(GtfsStorage.EdgeType.HOP).contains(encoder.getEdgeType(e.getFlags()))).map(e -> stopFromHopEdge(geometryFactory, e)).collect(Collectors.toList());
        return new Trip.PtLeg(stop, edges, departureTime, stops, edges.stream().mapToDouble(EdgeIteratorState::getDistance).sum(), lineString);
    }

    private static Geometry lineStringFromEdges(GeometryFactory geometryFactory, List<EdgeIteratorState> edges) {
        return Stream.concat(Stream.of(edges.get(0).fetchWayGeometry(3)),
                edges.stream().map(edge -> edge.fetchWayGeometry(2)))
                .flatMap(pointList -> pointList.toGeoJson().stream())
                    .map(doubles -> new Coordinate(doubles[0], doubles[1]))
                    .collect(Collectors.collectingAndThen(Collectors.toList(),
                            coords -> geometryFactory.createLineString(coords.toArray(new Coordinate[]{}))));
    }

    private static Trip.Stop stopFromHopEdge(GeometryFactory geometryFactory, EdgeIteratorState e) {
        return new Trip.Stop(e.getName(), geometryFactory.createPoint(new Coordinate(e.fetchWayGeometry(2).getLon(0), e.fetchWayGeometry(2).getLat(0))));
    }

}
