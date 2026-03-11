package com.example.interviewplatform.skill.repository

import com.example.interviewplatform.skill.entity.CareerBenchmarkEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CareerBenchmarkRepository : JpaRepository<CareerBenchmarkEntity, Long> {
    fun findByJobRoleIdAndExperienceBandCode(jobRoleId: Long, experienceBandCode: String): List<CareerBenchmarkEntity>
}
