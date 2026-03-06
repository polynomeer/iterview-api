package com.example.interviewplatform.user.repository

import com.example.interviewplatform.user.entity.UserTargetCompanyEntity
import com.example.interviewplatform.user.entity.UserTargetCompanyId
import org.springframework.data.jpa.repository.JpaRepository

interface UserTargetCompanyRepository : JpaRepository<UserTargetCompanyEntity, UserTargetCompanyId> {
    fun findByIdUserIdOrderByPriorityOrderAsc(userId: Long): List<UserTargetCompanyEntity>

    fun deleteByIdUserId(userId: Long)
}
