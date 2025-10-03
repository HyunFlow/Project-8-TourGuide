package com.openclassrooms.tourguide.dto;

import gpsUtil.location.Attraction;

public class NearestAttractionDTO {
    private String attractionName;
    private double longitude;
    private double latitude;
    private double distance;
    private int rewardPoint;

    public NearestAttractionDTO(Attraction attraction, double distance, int rewardPoint) {
        this.attractionName = attraction.attractionName;
        this.longitude = attraction.longitude;
        this.latitude = attraction.latitude;
        this.distance = distance;
        this.rewardPoint = rewardPoint;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getRewardPoint() {
        return rewardPoint;
    }

    public void setRewardPoint(int rewardPoint) {
        this.rewardPoint = rewardPoint;
    }
}
