package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeVersionRepository : JpaRepository<ResumeVersionEntity, Long>
