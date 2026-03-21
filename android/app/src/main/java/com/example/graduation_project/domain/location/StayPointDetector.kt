package com.example.graduation_project.domain.location

interface StayPointDetector {
    fun detect(locations: List<LocationPoint>): List<StayPoint>
}
