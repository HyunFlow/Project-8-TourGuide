package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearestAttractionDTO;
import com.openclassrooms.tourguide.geo.GeoUtils;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {

    private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);
    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();

    private final ExecutorService locationExecutor = new ThreadPoolExecutor(12, 24, 60,
        TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), new ThreadPoolExecutor.CallerRunsPolicy());

    public Tracker tracker;
    boolean testMode = true;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }
        tracker = new Tracker(this);
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation =
            (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return internalUserMap.values().stream().collect(Collectors.toList());
    }

    public void addUser(User user) {
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }

    public List<Provider> getTripDeals(User user) {
        int cumulatativeRewardPoints = user.getUserRewards().stream()
            .mapToInt(UserReward::getRewardPoints).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
            user.getUserPreferences().getNumberOfAdults(),
            user.getUserPreferences().getNumberOfChildren(),
            user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    public void trackMultipleUsersLocation(List<User> users) {
        CompletableFuture<?> tasks = CompletableFuture.allOf(
            users.stream()
                .map(u -> CompletableFuture.runAsync(() -> trackUserLocation(u), locationExecutor))
                .toArray(CompletableFuture[]::new)
        );
        tasks.join();
    }

    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation currentLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(currentLocation);
        rewardsService.calculateRewards(user);
        return currentLocation;
    }

    public List<NearestAttractionDTO> getNearByAttractions(String userName, int limit) {
        if (limit <= 0) {
            limit = 5;
        }

        User user = getUser(userName);
        Location lastLocation = getUserLocation(user).location;

        List<Attraction> attractions = List.copyOf(gpsUtil.getAttractions());

        PriorityQueue<NearestAttractionDTO> pq = new PriorityQueue<>(
            Comparator.comparingDouble(NearestAttractionDTO::getDistance).reversed());

        for (Attraction attraction : attractions) {
            double distance = GeoUtils.getDistance(attraction, lastLocation);
            pq.offer(new NearestAttractionDTO(attraction, distance,
                rewardsService.getRewardPoints(attraction, user)));
            if (pq.size() > limit) {
                pq.poll();
            }
        }

        List<NearestAttractionDTO> result = new ArrayList<>(pq);
        result.sort(Comparator.comparingDouble(NearestAttractionDTO::getDistance));
        return result;
    }

    @PreDestroy
    void shutdownPool() {
        logger.info("Shutting down TourGuideService resources...");
        if (tracker != null) {
            try {
                tracker.stopTracking();
            } catch (Exception e) {
                logger.warn("Failed to stop tracking service", e);
            }
        }
        locationExecutor.shutdown();
        try {
            if (!locationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                locationExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            locationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Shutdown completed");
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";
    // Database connection will be used for external users, but for testing purposes
// internal users are provided and stored in memory
    private final Map<String, User> internalUserMap = new HashMap<>();

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug(
            "Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                new Location(generateRandomLatitude(), generateRandomLongitude()),
                getRandomTime()));
        });
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

}
