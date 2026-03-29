package com.example.graduation_project.domain.health

import com.example.graduation_project.domain.model.LocationPoint
import com.example.graduation_project.domain.model.StayPoint

interface StayPointDetector {
    fun detect(locations: List<LocationPoint>): List<StayPoint>
}
