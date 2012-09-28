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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.Helper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class MMapDirectory implements Directory {

    private Map<String, MMapDataAccess> map = new HashMap<String, MMapDataAccess>();
    private String location;

    public MMapDirectory() {
    }

    public MMapDirectory(String _location) {
        this.location = _location;
        if (location.isEmpty() || location == null)
            location = new File("").getAbsolutePath();
        if (!location.endsWith("/"))
            location += "/";
        new File(location).mkdirs();
    }

    @Override
    public DataAccess createDataAccess(String name) {
        name = location + name;
        if (map.containsKey(name))
            throw new IllegalStateException("DataAccess " + name + " already exists");

        MMapDataAccess da = new MMapDataAccess(name);
        map.put(name, da);
        return da;
    }

    @Override
    public String getLocation() {
        return location;
    }

//    @Override
//    public void clear() {
//        Helper.deleteDir(new File(location));
//        new File(location).mkdirs();
//        map.clear();
//    }
}
