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
package com.graphhopper.util.details;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds PathDetails, from values and intervals of a Path.
 *
 * @author Robin Boldt
 */
public class PathDetailsBuilder {

    private final String name;
    private boolean isOpen = false;

    private PathDetail currentDetail = new PathDetail();
    private List<PathDetail> pathDetails = new ArrayList<>();

    public PathDetailsBuilder(String name) {
        this.name = name;
    }

    /**
     * It is only possible to open one interval at a time. Calling <code>startInterval</code> when
     * the interval is already open results in an Exception.
     *
     * @param value The value of the Path at this moment, that should be stored in the PathDetail
     */
    public void startInterval(Object value) {
        if (this.isOpen) {
            throw new IllegalStateException("Path details is already open with value: " + this.currentDetail.value + " trying to open a new one with value: " + value);
        }
        this.currentDetail = new PathDetail(value);
        this.isOpen = true;
    }

    /**
     * Ending intervals multiple times is safe, we only write the interval if it was open and not empty.
     * Writes the interval to the pathDetails
     *
     * @param numberOfPoints Length of the PathDetail
     */
    public void endInterval(int numberOfPoints) {
        // We don't want PathDetails
        if (this.isOpen && numberOfPoints > 0) {
            this.currentDetail.numberOfPoints = numberOfPoints;
            this.pathDetails.add(this.currentDetail);
        }
        this.isOpen = false;
    }

    public PathDetails buildPathDetails() {
        return new PathDetails(this.getName(), this.pathDetails);
    }

    public String getName() {
        return this.name;
    }
}