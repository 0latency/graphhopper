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
package de.jetsli.graph.util;

/**
 * This class implements a rather quick solution to calculate 3D distances on earth using euclidean
 * geometry mixed with Haversine formula used for the on earth distance. The haversine formula makes
 * not so much sense as it is only important for large distances where then the rather smallish
 * heights would becomes neglectable.
 *
 * @author Peter Karich
 */
public class DistanceCalc3D extends DistanceCalc {

    /**
     * @param fromHeight in meters above 0
     * @param toHeight in meters above 0
     */
    public double calcDistKm(double fromLat, double fromLon, double fromHeight,
            double toLat, double toLon, double toHeight) {
        double len = super.calcDistKm(fromLat, fromLon, toLat, toLon);
        double delta = Math.abs(toHeight - fromHeight) / 1000;
        return Math.sqrt(delta * delta + len * len);
    }
}
