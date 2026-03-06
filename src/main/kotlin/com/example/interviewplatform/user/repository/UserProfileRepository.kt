package com.example.interviewplatform.user.repository

import com.example.interviewplatform.user.entity.UserProfileEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileRepository : JpaRepository<UserProfileEntity, Long>
