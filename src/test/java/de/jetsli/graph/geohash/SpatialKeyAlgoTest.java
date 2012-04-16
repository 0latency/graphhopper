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
package de.jetsli.graph.geohash;

import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.reader.CalcDistance;
import de.jetsli.graph.util.BitUtil;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.CoordTrig;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialKeyAlgoTest {

    @Test
    public void testEncode() {
        SpatialKeyAlgo algo = new SpatialKeyAlgo().init(32);
        long val = algo.encode(-24.235345f, 47.234234f);
        assertEquals("01100110101000111100000110010100", BitUtil.toBitString(val, 32));
    }

    @Test
    public void testEncode3BytesPrecision() {
        // 3 bytes => c / 1^12 = ~10km
        int bits = 3 * 8;
        SpatialKeyAlgo algo = new SpatialKeyAlgo().init(bits);
        float lat = 24.235345f;
        float lon = 47.234234f;
        long val = algo.encode(lat, lon);
        assertEquals("00000000" + "110011000000100101101011", BitUtil.toBitString(val, 32));

        CoordTrig fl = new CoordTrig();
        algo.decode(val, fl);
        // normally 10km are expected here we have only 100meters ... (?)
        assertEquals(lat, fl.lat, .1);
        assertEquals(lon, fl.lon, .1);

        double expectedDist = ((float) CalcDistance.C / (1 << bits / 2));
        double d = new CalcDistance().calcDistKm(lat, lon, fl.lat, fl.lon);
        assertTrue("Returned point shouldn't be more far away than " + expectedDist + " -> It was " + d, d < expectedDist);
    }

    @Test
    public void testEncode4BytesPrecision() {
        int bits = 4 * 8;
        SpatialKeyAlgo algo = new SpatialKeyAlgo().init(bits);
        float lat = 24.235345f;
        float lon = 47.234234f;
        long val = algo.encode(lat, lon);
        assertEquals("11001100000010010110101100111110", BitUtil.toBitString(val, bits));

        CoordTrig fl = new CoordTrig();
        algo.decode(val, fl);
        assertEquals(lat, fl.lat, 1e-2);
        assertEquals(lon, fl.lon, 1e-2);

        double expectedDist = ((float) CalcDistance.C / (1 << bits / 2));
        double d = new CalcDistance().calcDistKm(lat, lon, fl.lat, fl.lon);
        assertTrue("Returned point shouldn't be more far away than " + expectedDist + " -> It was " + d, d < expectedDist);
    }

    @Test
    public void testEncode6BytesPrecision() {
        int bits = 6 * 8;
        SpatialKeyAlgo algo = new SpatialKeyAlgo().init(bits);
        float lat = 24.235345f;
        float lon = 47.234234f;
        long val = algo.encode(lat, lon);
        assertEquals("11001100000010010110101100111110" + "11100111" + "01000110", BitUtil.toBitString(val, bits));

        CoordTrig fl = new CoordTrig();
        algo.decode(val, fl);
        assertEquals(lat, fl.lat, 1e-4);
        assertEquals(lon, fl.lon, 1e-4);

        double expectedDist = ((float) CalcDistance.C / (1 << bits / 2));
        double d = new CalcDistance().calcDistKm(lat, lon, fl.lat, fl.lon);
        assertTrue("Returned point shouldn't be more far away than " + expectedDist + " -> It was " + d, d < expectedDist);
    }

    @Test
    public void testBijectionBug2() {
        for (long i = 4; i <= 64; i += 4) {
            SpatialKeyAlgo algo = new SpatialKeyAlgo().init((int) i);
            long keyX = algo.encode(1, 1);

            CoordTrig coord = new CoordTrig();
            algo.decode(keyX, coord);
            long keyY = algo.encode(coord.lat, coord.lon);

            CoordTrig coord2 = new CoordTrig();
            algo.decode(keyY, coord2);

            double precision = CalcDistance.C / (1 << (i / 2 - 2)) / 4;
            double dist = new CalcDistance().calcDistKm(coord.lat, coord.lon, coord2.lat, coord2.lon);
            assertEquals(0, dist, 1e-5);
//            System.out.println("\n\n##" + i + "\nkeyX:" + BitUtil.toBitString(keyX));
//            System.out.println("keyY:" + BitUtil.toBitString(keyY));
//            System.out.println("distanceX:" + dist + " precision:" + precision + " difference:" + (dist - precision) + " factor:" + dist / precision);
        }
    }
    
    @Test
    public void testBijection() {
        SpatialKeyAlgo algo = new SpatialKeyAlgo().init(6 * 8);
        CoordTrig coord11 = new CoordTrig();
        long key = algo.encode(1, 1);
        algo.decode(key, coord11);
        long resKey = algo.encode(coord11.lat, coord11.lon);
        CoordTrig coord2 = new CoordTrig();
        algo.decode(resKey, coord2);
        assertEquals(key, resKey);

        CoordTrig coord = new CoordTrig(50.022846, 9.2123575);
        key = algo.encode(coord);
        algo.decode(key, coord2);
        
        // 1. wont fix bijection precision problem
        // assertEquals(key, algo.encode(coord2));
        double dist = new CalcDistance().calcDistKm(coord.lat, coord.lon, coord2.lat, coord2.lon);
        // but ensure small distance
        assertTrue(dist + "", dist < 1e-2);

        long queriedKey = 246557819640268L;
        long storedKey = 246557819640269L;
        algo.decode(queriedKey, coord);
        algo.decode(storedKey, coord2);
        
        // 2. wont fix bijection precision problem
        // assertEquals(storedKey, algo.encode(coord2));
        dist = new CalcDistance().calcDistKm(coord.lat, coord.lon, coord2.lat, coord2.lon);
        // but ensure small distance
        assertTrue(dist + "", dist < 1e-2);
    }
}
