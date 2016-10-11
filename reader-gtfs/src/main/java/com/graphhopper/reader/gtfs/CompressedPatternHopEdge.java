package com.graphhopper.reader.gtfs;

import com.graphhopper.util.HeuristicCAPCompressor;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

class CompressedPatternHopEdge extends AbstractPatternHopEdge {

	private final Set<FrequencyEntry> frequencies = new HashSet<>();

	CompressedPatternHopEdge(SortedMap<Integer, Integer> departureTimeXTravelTime) {
		super();
		departureTimeXTravelTime.entrySet().stream().collect(Collectors.groupingBy(e -> e.getValue())).forEach( (travelTime, departures) -> {
			List<Integer> departuresForOneTravelTime = departures.stream().map(e -> e.getKey()).collect(Collectors.toList());
			List<HeuristicCAPCompressor.ArithmeticProgression> compress = HeuristicCAPCompressor.compress(departuresForOneTravelTime);
			for (HeuristicCAPCompressor.ArithmeticProgression ap : compress) {
				FrequencyEntry frequencyEntry = new FrequencyEntry();
				frequencyEntry.ap = ap;
				frequencyEntry.travelTime = travelTime;
				frequencies.add(frequencyEntry);
			}
		});
	}

	@Override
	double nextTravelTimeIncludingWaitTime(double earliestStartTime) {
		double result = Double.POSITIVE_INFINITY;
		for (FrequencyEntry frequency : frequencies) {
			double cost = evaluate(frequency, earliestStartTime);
			if (cost < result) {
				result = cost;
			}
		}
		return result;
	}

	private double evaluate(FrequencyEntry frequency, double earliestStartTime) {
		if (earliestStartTime < frequency.ap.a) {
			return (frequency.ap.a - earliestStartTime + frequency.travelTime);
		} else if (earliestStartTime <= frequency.ap.b) {
			return (frequency.ap.a + Math.ceil( (earliestStartTime - frequency.ap.a) / frequency.ap.p) * frequency.ap.p - earliestStartTime + frequency.travelTime);
		} else {
			return Double.POSITIVE_INFINITY;
		}
	}

	private static class FrequencyEntry implements Serializable {
		public HeuristicCAPCompressor.ArithmeticProgression ap;
		public Integer travelTime;
	}
}
