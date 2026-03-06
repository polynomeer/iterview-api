package com.example.interviewplatform.user.repository

import com.example.interviewplatform.user.entity.JobRoleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JobRoleRepository : JpaRepository<JobRoleEntity, Long>
