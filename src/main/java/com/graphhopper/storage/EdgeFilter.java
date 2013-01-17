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
package com.graphhopper.storage;

import com.graphhopper.util.BitUtil;

/**
 * @author Peter Karich
 */
public class EdgeFilter {

    /**
     * no filter active
     */
    public final static EdgeFilter ALL = new EdgeFilter(0xFFFFFFFF);
    /**
     * only outgoing edges will be selected
     */
    public final static EdgeFilter OUT = new EdgeFilter(1);
    /**
     * only incoming edges will be selected
     */
    public final static EdgeFilter IN = new EdgeFilter(2);
    /**
     * only (virtual) edges to tower nodes will be selected
     */
    public final static EdgeFilter TOWER_NODES = new EdgeFilter(4);    
    private final int value;

    public EdgeFilter(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    EdgeFilter combine(EdgeFilter filter) {
        return new EdgeFilter(value | filter.value);
    }

    boolean contains(EdgeFilter filter) {
        return (value & filter.value) != 0;
    }

    @Override
    public String toString() {
        return BitUtil.toBitString(value, 32);
    }
}
