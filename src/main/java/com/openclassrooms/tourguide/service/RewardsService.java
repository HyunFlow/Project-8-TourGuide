package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.geo.GeoUtils;
import java.util.ArrayList;
import java.util.List;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {

    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    // proximity in miles
    private int defaultProximityBuffer = 10;
    private int proximityBuffer = defaultProximityBuffer;
    private int attractionProximityRange = 200;
    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;

    private final ExecutorService rewardsExecutor = new ThreadPoolExecutor(128, 512, 60,
        TimeUnit.SECONDS, new LinkedBlockingQueue<>(20_000), new ThreadPoolExecutor.CallerRunsPolicy());

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = defaultProximityBuffer;
    }

    public void calculateMultipleRewards(List<User> users) {
        CompletableFuture<?> tasks = CompletableFuture.allOf(
            users.stream()
                .map(u -> CompletableFuture.runAsync(() -> calculateRewards(u), rewardsExecutor))
                .toArray(CompletableFuture[]::new)
        );
        tasks.join();
    }

    public void calculateRewards(User user) {
        List<VisitedLocation> userLocations = List.copyOf(user.getVisitedLocations());
        List<Attraction> attractions = List.copyOf(gpsUtil.getAttractions());

        Set<String> rewarded = user.getUserRewards().stream()
            .map(r -> r.attraction.attractionName)
            .collect(Collectors.toSet());

        for (VisitedLocation visitedLocation : userLocations) {
            for (Attraction attraction : attractions) {
                String attractionName = attraction.attractionName;
                if (!rewarded.contains(attractionName) && nearAttraction(visitedLocation, attraction)) {
                        user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
                        rewarded.add(attractionName);
                }
            }
        }
    }

    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return GeoUtils.getDistance(attraction, location) > attractionProximityRange ? false : true;
    }

    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return GeoUtils.getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
    }

    public int getRewardPoints(Attraction attraction, User user) {
        return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    }

}
