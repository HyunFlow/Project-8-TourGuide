package com.openclassrooms.tourguide;

import com.openclassrooms.tourguide.dto.NearestAttractionDTO;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
@RequestMapping("/api")
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @GetMapping("/users/{userName}/location")
    public VisitedLocation getLocation(@PathVariable String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }

    @GetMapping("/users/{userName}/attractions/nearby")
    public List<NearestAttractionDTO> getNearbyAttractions(@PathVariable String userName, @RequestParam(defaultValue = "5") int limit) {
    	return tourGuideService.getNearByAttractions(userName, limit);
    }
    
    @GetMapping("/users/{userName}/rewards")
    public List<UserReward> getRewards(@PathVariable String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @GetMapping("/users/{userName}/tripDeals")
    public List<Provider> getTripDeals(@PathVariable String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}