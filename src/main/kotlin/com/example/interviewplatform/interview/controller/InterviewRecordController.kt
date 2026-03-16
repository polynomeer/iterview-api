package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.interview.dto.InterviewRecordAnalysisDto
import com.example.interviewplatform.interview.dto.InterviewRecordDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordListItemDto
import com.example.interviewplatform.interview.dto.InterviewRecordQuestionsResponseDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewDto
import com.example.interviewplatform.interview.dto.InterviewRecordTranscriptDto
import com.example.interviewplatform.interview.dto.BulkUpdateInterviewTranscriptSegmentsRequest
import com.example.interviewplatform.interview.dto.UpdateInterviewTranscriptSegmentRequest
import com.example.interviewplatform.interview.dto.InterviewerProfileDto
import com.example.interviewplatform.interview.service.InterviewRecordService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@Validated
@Tag(name = "Interview Record")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/interview-records")
class InterviewRecordController(
    private val interviewRecordService: InterviewRecordService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List imported interview records")
    fun listRecords(): List<InterviewRecordListItemDto> =
        interviewRecordService.listRecords(currentUserProvider.currentUserId())

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload and create imported interview record")
    fun createRecord(
        @RequestPart("file") file: MultipartFile,
        @RequestParam("companyName", required = false) companyName: String?,
        @RequestParam("roleName", required = false) roleName: String?,
        @RequestParam("interviewDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) interviewDate: LocalDate?,
        @RequestParam("interviewType", required = false) interviewType: String?,
        @RequestParam("linkedResumeVersionId", required = false) linkedResumeVersionId: Long?,
        @RequestParam("linkedJobPostingId", required = false) linkedJobPostingId: Long?,
        @RequestParam("transcriptText", required = false) transcriptText: String?,
    ): InterviewRecordDetailDto = interviewRecordService.createRecord(
        userId = currentUserProvider.currentUserId(),
        file = file,
        companyName = companyName,
        roleName = roleName,
        interviewDate = interviewDate,
        interviewType = interviewType,
        linkedResumeVersionId = linkedResumeVersionId,
        linkedJobPostingId = linkedJobPostingId,
        transcriptText = transcriptText,
    )

    @GetMapping("/{recordId}")
    @Operation(summary = "Get interview record detail")
    fun getRecord(@PathVariable recordId: Long): InterviewRecordDetailDto =
        interviewRecordService.getRecord(currentUserProvider.currentUserId(), recordId)

    @GetMapping("/{recordId}/transcript")
    @Operation(summary = "Get interview transcript detail")
    fun getTranscript(@PathVariable recordId: Long): InterviewRecordTranscriptDto =
        interviewRecordService.getTranscript(currentUserProvider.currentUserId(), recordId)

    @PatchMapping("/{recordId}/transcript/segments/{segmentId}")
    @Operation(summary = "Update one transcript segment")
    fun updateTranscriptSegment(
        @PathVariable recordId: Long,
        @PathVariable segmentId: Long,
        @Valid @org.springframework.web.bind.annotation.RequestBody request: UpdateInterviewTranscriptSegmentRequest,
    ): InterviewRecordTranscriptDto =
        interviewRecordService.updateTranscriptSegment(currentUserProvider.currentUserId(), recordId, segmentId, request)

    @GetMapping("/{recordId}/questions")
    @Operation(summary = "Get structured interview questions and answers")
    fun getQuestions(@PathVariable recordId: Long): InterviewRecordQuestionsResponseDto =
        interviewRecordService.listQuestions(currentUserProvider.currentUserId(), recordId)

    @GetMapping("/{recordId}/review")
    @Operation(summary = "Get practical interview review provenance")
    fun getReview(@PathVariable recordId: Long): InterviewRecordReviewDto =
        interviewRecordService.getReview(currentUserProvider.currentUserId(), recordId)

    @PatchMapping("/{recordId}/review")
    @Operation(summary = "Apply bulk transcript review edits and optionally confirm")
    fun applyReview(
        @PathVariable recordId: Long,
        @Valid @org.springframework.web.bind.annotation.RequestBody request: BulkUpdateInterviewTranscriptSegmentsRequest,
    ): InterviewRecordReviewDto =
        interviewRecordService.applyReview(currentUserProvider.currentUserId(), recordId, request)

    @GetMapping("/{recordId}/analysis")
    @Operation(summary = "Get imported interview analysis")
    fun getAnalysis(@PathVariable recordId: Long): InterviewRecordAnalysisDto =
        interviewRecordService.getAnalysis(currentUserProvider.currentUserId(), recordId)

    @GetMapping("/{recordId}/interviewer-profile")
    @Operation(summary = "Get inferred interviewer profile")
    fun getInterviewerProfile(@PathVariable recordId: Long): InterviewerProfileDto =
        interviewRecordService.getInterviewerProfile(currentUserProvider.currentUserId(), recordId)

    @PostMapping("/{recordId}/confirm")
    @Operation(summary = "Confirm practical interview structuring review")
    fun confirmRecord(@PathVariable recordId: Long): InterviewRecordDetailDto =
        interviewRecordService.confirmRecord(currentUserProvider.currentUserId(), recordId)

    @PostMapping("/{recordId}/retry-transcription")
    @Operation(summary = "Retry practical interview transcription")
    fun retryTranscription(@PathVariable recordId: Long): InterviewRecordDetailDto =
        interviewRecordService.retryTranscription(currentUserProvider.currentUserId(), recordId)
}
