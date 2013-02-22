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
package com.graphhopper.routing;

import com.graphhopper.routing.util.CarFlagsEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagsEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm {

    protected Graph graph;
    protected WeightCalculation weightCalc;
    protected EdgeFilter outEdgeFilter;
    protected FlagsEncoder flagsEncoder;

    public AbstractRoutingAlgorithm(Graph graph) {
        this.graph = graph;
        type(new ShortestCalc()).vehicle(new CarFlagsEncoder());
    }

    @Override public RoutingAlgorithm vehicle(FlagsEncoder encoder) {
        this.flagsEncoder = encoder;
        outEdgeFilter = new DefaultEdgeFilter(encoder).direction(false, true);
        return this;
    }

    @Override public RoutingAlgorithm type(WeightCalculation wc) {
        this.weightCalc = wc;
        return this;
    }

    protected void updateShortest(EdgeEntry shortestDE, int currLoc) {        
    }

    @Override public String toString() {
        return name() + "|" + weightCalc;
    }

    @Override public String name() {
        return getClass().getSimpleName();
    }
}
