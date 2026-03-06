package com.example.interviewplatform.user.service

import com.example.interviewplatform.user.entity.CompanyEntity
import com.example.interviewplatform.user.repository.CompanyRepository
import org.springframework.stereotype.Service

@Service
class TargetCompanyService(
    private val companyRepository: CompanyRepository,
) {
    fun listCompanies(): List<CompanyEntity> = companyRepository.findAllByOrderByNameAsc()
}
