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
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public abstract class DataAccessTest {

    private File folder = new File("./target/tmp/");
    protected String location;

    public abstract DataAccess createDataAccess(String location);

    @Before
    public void setUp() {
        Helper.deleteDir(folder);
        folder.mkdirs();
        location = folder.getAbsolutePath() + "/dataacess";
    }

    @After
    public void tearDown() {
        Helper.deleteDir(folder);
    }

    @Test
    public void testLoadFlush() {
        DataAccess da = createDataAccess(location);
        assertFalse(da.loadExisting());
        da.createNew(300);
        da.setInt(7, 123);
        assertEquals(123, da.getInt(7));
        da.setInt(10, Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, da.getInt(10));
        da.flush();

        // check noValue clearing
        assertEquals(0, da.getInt(2));
        assertEquals(0, da.getInt(3));
        assertEquals(123, da.getInt(7));
        assertEquals(Integer.MAX_VALUE / 3, da.getInt(10));
        da.close();

        // cannot load data if already closed
        assertFalse(da.loadExisting());

        da = createDataAccess(location);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7));
        da.close();
    }

    @Test
    public void testLoadClose() {
        DataAccess da = createDataAccess(location);
        assertFalse(da.loadExisting());
        // throw some undefined exception if no ensureCapacity was called
        try {
            da.setInt(2, 321);
        } catch (Exception ex) {
        }

        da.createNew(300);
        da.setInt(2, 321);
        da.flush();
        da.close();
        da = createDataAccess(location);
        assertTrue(da.loadExisting());
        assertEquals(321, da.getInt(2));
        da.close();
    }

    @Test
    public void testHeader() {
        DataAccess da = createDataAccess(location);
        da.createNew(300);
        da.setHeader(7, 123);
        assertEquals(123, da.getHeader(7));
        da.setHeader(10, Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, da.getHeader(10));
        da.flush();

        da = createDataAccess(location);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getHeader(7));
        da.close();
    }

    @Test
    public void testEnsureCapacity() {
        DataAccess da = createDataAccess(location);
        da.createNew(128);
        da.setInt(31, 200);
        try {
            // this should fail with an index out of bounds exception
            da.setInt(32, 220);
            assertFalse(true);
        } catch (Exception ex) {
        }
        assertEquals(200, da.getInt(31));
        da.ensureCapacity(2 * 128);
        assertEquals(200, da.getInt(31));
        // now it shouldn't fail now
        da.setInt(32, 220);
        assertEquals(220, da.getInt(32));

        // ensure some bigger area
        da = createDataAccess(location);
        da.createNew(200 * 4);
        da.ensureCapacity(600 * 4);
    }

    @Test
    public void testCopy() {
        DataAccess da = createDataAccess(location);
        da.createNew(1001 * 4);
        da.setInt(1, 1);
        da.setInt(123, 321);
        da.setInt(1000, 1111);

        DataAccess da2 = createDataAccess(location + "2");
        da2.createNew(10);
        da.copyTo(da2);
        assertEquals(1, da2.getInt(1));
        assertEquals(321, da2.getInt(123));
        assertEquals(1111, da2.getInt(1000));

        da2.setInt(1, 2);
        assertEquals(2, da2.getInt(1));
        da2.flush();
        da.flush();
        // make sure they are independent!
        assertEquals(1, da.getInt(1));
    }

    @Test
    public void testSegments() {
        DataAccess da = createDataAccess(location);
        da.setSegmentSize(128);
        da.createNew(10);
        assertEquals(1, da.getSegments());
        da.ensureCapacity(500);
        int olds = da.getSegments();
        assertTrue(olds > 3);

        da.setInt(400 / 4, 321);
        da.flush();
        da.close();

        da = createDataAccess(location);
        assertTrue(da.loadExisting());
        assertEquals(olds, da.getSegments());
        assertEquals(321, da.getInt(400 / 4));
    }
}
