package com.example.interviewplatform.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class UserTargetCompanyId(
    @Column(name = "user_id")
    val userId: Long = 0,
    @Column(name = "company_id")
    val companyId: Long = 0,
) : Serializable
