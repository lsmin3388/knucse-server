package com.knucse.locker.api.locker.applyForm._executive;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knucse.common.util.api.ApiResponseUtil;
import com.knucse.common.util.api.ApiSuccessResult;
import com.knucse.locker.api.locker.applyForm.dto.ApplyFormCreateDto;
import com.knucse.locker.api.locker.applyForm.dto.ApplyFormReadDto;
import com.knucse.locker.api.locker.applyForm.dto.ApplyFormUpdateDto;
import com.knucse.locker.domain.service.locker.applyForm.ApplyFormService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/forms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EXECUTIVE') and isAuthenticated()")
@Tag(name = "신청 폼 관리 (집행부)", description = "집행부용 신청 폼 관리 API")
public class ApplyFormExecutiveController {
	private final ApplyFormService applyFormService;

	@PostMapping
	@Operation(summary = "신청 폼 생성", description = "집행부는 새로운 신청 폼을 생성할 수 있습니다.")
	@ApiResponses(value = {
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신청 폼 생성 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신청 폼이 이미 존재함 (code: APPLY_FORM_DUPLICATED)")
	})
	public ResponseEntity<ApiSuccessResult<ApplyFormReadDto>> createForm(
		@Valid @RequestBody ApplyFormCreateDto requestBody
	) {
		ApplyFormReadDto responseBody = applyFormService.createApplyForm(requestBody);
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponseUtil.success(HttpStatus.CREATED, responseBody));
	}

	@PutMapping("/{year}/{semester}")
	@Operation(summary = "신청 폼 수정", description = "집행부는 특정 연도와 학기의 신청 폼을 수정할 수 있습니다.")
	@ApiResponses(value = {
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신청 폼 수정 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신청 폼을 찾을 수 없음 (code: APPLY_FORM_NOT_FOUND)")
	})
	public ResponseEntity<ApiSuccessResult<ApplyFormReadDto>> updateForm(
		@PathVariable Integer year,
		@PathVariable Integer semester,
		@RequestBody ApplyFormUpdateDto requestBody
	) {
		ApplyFormReadDto updatedForm = applyFormService.updateApplyForm(year, semester, requestBody);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponseUtil.success(HttpStatus.OK, updatedForm));
	}

	@DeleteMapping("/{year}/{semester}")
	@Operation(summary = "신청 폼 삭제", description = "집행부는 특정 연도와 학기의 신청 폼을 삭제할 수 있습니다.")
	@ApiResponses(value = {
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "신청 폼 삭제 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신청 폼을 찾을 수 없음 (code: APPLY_FORM_NOT_FOUND)")
	})
	public ResponseEntity<ApiSuccessResult<Void>> deleteForm(
		@PathVariable Integer year,
		@PathVariable Integer semester
	) {
		applyFormService.deleteApplyForm(year, semester);
		return ResponseEntity
			.status(HttpStatus.NO_CONTENT)
			.body(ApiResponseUtil.success(HttpStatus.NO_CONTENT));
	}

	@GetMapping
	@Operation(summary = "신청 폼 전체 조회", description = "집행부는 모든 신청 폼을 조회할 수 있습니다.")
	@ApiResponses(value = {
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신청 폼 조회 성공")
	})
	public ResponseEntity<ApiSuccessResult<List<ApplyFormReadDto>>> getAllForms() {
		List<ApplyFormReadDto> forms = applyFormService.getAllApplyForms();
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponseUtil.success(HttpStatus.OK, forms));
	}

	@GetMapping("/{year}/{semester}")
	@Operation(summary = "신청 폼 조회", description = "집행부는 특정 연도와 학기의 신청 폼을 조회할 수 있습니다.")
	@ApiResponses(value = {
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신청 폼 조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신청 폼을 찾을 수 없음 (code: APPLY_FORM_NOT_FOUND)")
	})
	public ResponseEntity<ApiSuccessResult<ApplyFormReadDto>> getFormByYearAndSemester(
		@PathVariable Integer year,
		@PathVariable Integer semester
	) {
		ApplyFormReadDto form = applyFormService.getApplyForm(year, semester);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponseUtil.success(HttpStatus.OK, form));
	}

	@PatchMapping("/{year}/{semester}")
	@Operation(summary = "신청 폼 상태 변경", description = "집행부는 특정 연도와 학기의 신청 폼 상태를 변경할 수 있습니다.")
	@ApiResponses(value = {
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신청 폼 상태 변경 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신청 폼을 찾을 수 없음 (code: APPLY_FORM_NOT_FOUND)"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 활성화된 신청 폼이 존재함 (code: APPLY_FORM_DUPLICATED)")
	})
	public ResponseEntity<ApiSuccessResult<ApplyFormReadDto>> updateFormStatus(
		@PathVariable Integer year,
		@PathVariable Integer semester
	) {
		ApplyFormReadDto updatedForm = applyFormService.updateApplyFormStatus(year, semester);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponseUtil.success(HttpStatus.OK, updatedForm));
	}
}
