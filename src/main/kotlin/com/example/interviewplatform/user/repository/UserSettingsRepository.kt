package com.example.interviewplatform.user.repository

import com.example.interviewplatform.user.entity.UserSettingsEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserSettingsRepository : JpaRepository<UserSettingsEntity, Long>
