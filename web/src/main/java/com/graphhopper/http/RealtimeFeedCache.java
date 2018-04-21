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

package com.graphhopper.http;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.storage.GraphHopperStorage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RealtimeFeedCache {

    private GraphHopperStorage graphHopperStorage;
    private GtfsStorage gtfsStorage;
    private PtFlagEncoder ptFlagEncoder;
    private List<RealtimeFeedConfiguration> configurations;

    private LoadingCache<String, RealtimeFeed> cache = CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(new CacheLoader<String, RealtimeFeed>() {
                        public RealtimeFeed load(String key) {
                            RealtimeFeedConfiguration configuration = getConfiguration(key);
                            GtfsRealtime.FeedMessage feedMessage = configuration.getFeedMessage();
                            RealtimeFeed realtimeFeed = RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, ptFlagEncoder, feedMessage, configuration.getFeedId(), configuration.getAgencyId());
                            return realtimeFeed;
                        }
                    });

    RealtimeFeedCache(GraphHopperStorage graphHopperStorage, GtfsStorage gtfsStorage, PtFlagEncoder ptFlagEncoder, List<RealtimeFeedConfiguration> gtfsrealtime) {
        this.graphHopperStorage = graphHopperStorage;
        this.gtfsStorage = gtfsStorage;
        this.ptFlagEncoder = ptFlagEncoder;
        this.configurations = gtfsrealtime;
    }

    public RealtimeFeed getRealtimeFeed(String feedId) {
        try {
            return cache.get(feedId);
        } catch (ExecutionException | RuntimeException e) {
            e.printStackTrace();
            return RealtimeFeed.empty(gtfsStorage);
        }
    }

    public RealtimeFeedConfiguration getConfiguration(String feedId) {
        for (RealtimeFeedConfiguration realtimeFeedConfiguration : configurations) {
            if (feedId.equals(realtimeFeedConfiguration.getFeedId())) {
                return realtimeFeedConfiguration;
            }
        }
        return null;
    }
}
