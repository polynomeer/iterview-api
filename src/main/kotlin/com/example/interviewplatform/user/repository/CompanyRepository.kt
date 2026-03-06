package com.example.interviewplatform.user.repository

import com.example.interviewplatform.user.entity.CompanyEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CompanyRepository : JpaRepository<CompanyEntity, Long> {
    fun findAllByOrderByNameAsc(): List<CompanyEntity>
}
