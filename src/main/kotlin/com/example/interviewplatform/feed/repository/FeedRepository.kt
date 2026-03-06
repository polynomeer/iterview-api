package com.example.interviewplatform.feed.repository

import com.example.interviewplatform.feed.entity.FeedItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FeedRepository : JpaRepository<FeedItemEntity, Long>
