/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package com.graphhopper.coll;

import java.util.Arrays;

/**
 * Taken from opentripplanner.
 */
public class IntDoubleBinHeap implements BinHeapWrapper<Integer, Number> {

    private static final double GROW_FACTOR = 2.0;
    private float[] prio;
    private int[] elem;
    private int size;
    private int capacity;

    public IntDoubleBinHeap() {
        this(1000);
    }

    public IntDoubleBinHeap(int capacity) {
        if (capacity < 10)
            capacity = 10;
        this.capacity = capacity;
        size = 0;
        elem = new int[capacity + 1];
        // 1-based indexing
        prio = new float[capacity + 1];
        // set sentinel
        prio[0] = Float.NEGATIVE_INFINITY;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Double peekValue() {
        return peek_value();
    }

    public double peek_value() {
        if (size > 0)
            return prio[1];
        else
            throw new IllegalStateException("An empty queue does not have a minimum key.");
    }

    @Override
    public Integer peekKey() {
        return peek_key();
    }

    public int peek_key() {
        if (size > 0)
            return elem[1];
        else
            throw new IllegalStateException("An empty queue does not have a minimum value.");
    }

    @Override
    public void update(Integer key, Number value) {
        update_(key, value.doubleValue());
    }

    public void update_(int key, double value) {
        // Perform "inefficient" but straightforward linear search 
        // for an element then change its key by sifting up or down
        int i;
        for (i = 1; i <= size; i++) {
            if (elem[i] == key)
                break;
        }
        if (i > size)
            return;

        if (value > prio[i]) {
            // sift up (as in extract)
            while (i * 2 <= size) {
                int child = i * 2;
                if (child != size && prio[child + 1] < prio[child])
                    child++;
                if (value > prio[child]) {
                    elem[i] = elem[child];
                    prio[i] = prio[child];
                    i = child;
                } else
                    break;
            }
            elem[i] = key;
            prio[i] = (float) value;
        } else {
            // sift down (as in insert_)
            while (prio[i / 2] > value) {
                elem[i] = elem[i / 2];
                prio[i] = prio[i / 2];
                i /= 2;
            }
            elem[i] = key;
            prio[i] = (float) value;
        }
    }

    @Override
    public void insert(Integer key, Number value) {
        insert_(key, value.doubleValue());
    }

    public void insert_(int e, double p) {
        int i;
        size += 1;
        if (size > capacity)
            ensureCapacity((int) (capacity * GROW_FACTOR));
        for (i = size; prio[i / 2] > p; i /= 2) {
            elem[i] = elem[i / 2];
            prio[i] = prio[i / 2];
        }
        elem[i] = e;
        prio[i] = (float) p;
    }

    @Override
    public Integer pollKey() {
        return poll_key();
    }

    public int poll_key() {
        int i, child;
        int minElem = elem[1];
        int lastElem = elem[size];
        double lastPrio = prio[size];
        if (size <= 0)
            throw new IllegalStateException("An empty queue does not have a minimum value.");
        size -= 1;
        for (i = 1; i * 2 <= size; i = child) {
            child = i * 2;
            if (child != size && prio[child + 1] < prio[child])
                child++;
            if (lastPrio > prio[child]) {
                elem[i] = elem[child];
                prio[i] = prio[child];
            } else
                break;
        }
        elem[i] = lastElem;
        prio[i] = (float) lastPrio;
        return minElem;
    }

    @Override
    public void ensureCapacity(int capacity) {
        // System.out.println("Growing queue to " + capacity);
        if (capacity < size)
            throw new IllegalStateException("BinHeap contains too many elements to fit in new capacity.");
        this.capacity = capacity;
        prio = Arrays.copyOf(prio, capacity + 1);
        elem = Arrays.copyOf(elem, capacity + 1);
    }

    @Override
    public void clear() {
        this.size = 0;

        // necessary?
        Arrays.fill(prio, 0f);
        Arrays.fill(elem, 0);
    }
}