package com.knucse.locker.domain.service.locker.apply;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.knucse.common.util.datetime.DateTimeUtil;
import com.knucse.locker.api.locker.apply.dto.ApplyCreateDto;
import com.knucse.locker.api.locker.apply.dto.ApplyReadDto;
import com.knucse.locker.api.locker.apply.dto.ApplyReportCreateDto;
import com.knucse.locker.api.locker.apply.dto.ApplyReportReadDto;
import com.knucse.locker.api.locker.apply.dto.ApplyUpdateDto;
import com.knucse.locker.api.locker.apply.dto.ReportStatusUpdateDto;
import com.knucse.locker.domain.exception.locker.allocate.AllocateNotFoundException;
import com.knucse.locker.domain.exception.locker.allocate.AlreadyAllocatedException;
import com.knucse.locker.domain.exception.locker.apply.ApplyDuplicatedException;
import com.knucse.locker.domain.exception.locker.apply.ApplyNotFoundException;
import com.knucse.locker.domain.exception.locker.applyForm.ApplyFormNotFoundException;
import com.knucse.locker.domain.exception.locker.report.ReportNotFoundException;
import com.knucse.student._exception.StudentNotFoundException;
import com.knucse.locker.domain.model.locker.apply.Apply;
import com.knucse.locker.domain.model.locker.apply.ApplyPeriod;
import com.knucse.locker.domain.model.locker.apply.ApplyStatus;
import com.knucse.locker.domain.model.locker.applyForm.ApplyForm;
import com.knucse.locker.domain.model.locker.report.Report;
import com.knucse.student.student.model.Student;
import com.knucse.locker.domain.persistence.AllocateRepository;
import com.knucse.locker.domain.persistence.ApplyRepository;
import com.knucse.locker.domain.service.locker.applyForm.ApplyFormService;
import com.knucse.locker.domain.service.locker.report.ReportService;
import com.knucse.student.student.service.StudentService;

import knu.univ.cse.server.core.domain.exception.locker.apply.InvalidApplyPeriodException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplyService {
	/* Internal Dependencies */
	private final ApplyRepository applyRepository;

	/* External Dependencies */
	private final AllocateRepository allocateRepository;
	private final ApplyFormService applyFormService;
	private final StudentService studentService;
	private final ReportService reportService;

	/**
	 * 1차 신청을 처리합니다.
	 *
	 * @param createDto 신청 생성 DTO
	 * @return 생성된 신청을 나타내는 DTO
	 * @throws ApplyDuplicatedException "APPLY_DUPLICATED"
	 * @throws InvalidApplyPeriodException "INVALID_APPLY_PERIOD"
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 */
	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public ApplyReadDto handlePrimaryApply(ApplyCreateDto createDto) {
		return processApplication(createDto, ApplyPeriod.PRIMARY, ApplyStatus.APPLY);
	}

	/**
	 * 추가 신청을 처리합니다.
	 *
	 * @param createDto 신청 생성 DTO
	 * @return 생성된 신청을 나타내는 DTO
	 * @throws ApplyDuplicatedException "APPLY_DUPLICATED"
	 * @throws InvalidApplyPeriodException "INVALID_APPLY_PERIOD"
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 */
	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public ApplyReadDto handleAdditionalApply(ApplyCreateDto createDto) {
		return processApplication(createDto, ApplyPeriod.ADDITIONAL, ApplyStatus.APPLY);
	}

	/**
	 * 교체 신청과 함께 고장신고를 처리합니다.
	 *
	 * @param createDto 교체 신청 및 보고서 생성 DTO
	 * @return 생성된 신청과 보고서를 나타내는 DTO
	 * @throws ApplyDuplicatedException "APPLY_DUPLICATED"
	 * @throws InvalidApplyPeriodException "INVALID_APPLY_PERIOD"
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public ApplyReportReadDto handleReplacementApply(ApplyReportCreateDto createDto) {
		ApplyReadDto applyReadDto = processApplication(createDto.apply(), ApplyPeriod.REPLACEMENT, ApplyStatus.BROKEN_APPLY);
		Report report = attachReport(createDto.content(), applyReadDto.applyId());
		return ApplyReportReadDto.of(applyReadDto, report);
	}

	/**
	 * 신청을 처리합니다.
	 *
	 * @param createDto 신청 생성 DTO
	 * @param period 신청 기간
	 * @return 생성된 신청을 나타내는 DTO
	 * @throws ApplyDuplicatedException "APPLY_DUPLICATED"
	 * @throws InvalidApplyPeriodException "INVALID_APPLY_PERIOD"
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 * @throws AllocateNotFoundException "ALLOCATE_NOT_FOUND"
	 * @throws AlreadyAllocatedException "ALREADY_ALLOCATED"
	 */
	private ApplyReadDto processApplication(ApplyCreateDto createDto, ApplyPeriod period, ApplyStatus applyStatus) {
		ApplyForm activeApplyForm = applyFormService.getActiveApplyForm();
		validateApplicationPeriod(activeApplyForm, period);

		Student student = retrieveStudent(createDto);
		if (existsApplyByStatus(student, activeApplyForm, applyStatus)) {
			throw new ApplyDuplicatedException();
		}

		if (period == ApplyPeriod.REPLACEMENT && !allocateRepository.existsByStudentAndApplyForm(student, activeApplyForm)) {
			throw new AllocateNotFoundException();
		} else if (period != ApplyPeriod.REPLACEMENT && allocateRepository.existsByStudentAndApplyForm(student, activeApplyForm)) {
			throw new AlreadyAllocatedException();
		}

		Apply apply = applyRepository.save(createDto.toEntity(student, period, activeApplyForm));

		return ApplyReadDto.fromEntity(apply, student);
	}

	/**
	 * 보고서를 신청에 첨부합니다.
	 *
	 * @param content 보고서 내용
	 * @param applyId 신청 식별자
	 * @return 생성된 보고서 엔티티
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	private Report attachReport(String content, Long applyId) {
		Apply apply = applyRepository.findById(applyId)
			.orElseThrow(ApplyNotFoundException::new);
		return reportService.writeReport(content, apply);
	}

	/**
	 * 신청 기간이 유효한지 검증합니다.
	 *
	 * @param applyForm 활성화된 신청 폼
	 * @param period 신청 기간
	 * @throws InvalidApplyPeriodException "INVALID_APPLY_PERIOD"
	 */
	private void validateApplicationPeriod(ApplyForm applyForm, ApplyPeriod period) {
		LocalDateTime now = DateTimeUtil.now();
		if (!period.isWithinPeriod(applyForm, now)) {
			throw new InvalidApplyPeriodException();
		}
	}

	/**
	 * 신청 생성 DTO를 기반으로 학생을 조회합니다.
	 *
	 * @param createDto 신청 생성 DTO
	 * @return 조회된 학생 엔티티
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 */
	private Student retrieveStudent(ApplyCreateDto createDto) {
		return studentService.findStudentByNameAndNumber(
			createDto.studentName(),
			createDto.studentNumber()
		);
	}

	/**
	 * 학번을 통해 신청을 조회합니다.
	 *
	 * @param studentNumber 학생 학번
	 * @return 조회된 신청을 나타내는 DTO
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 */
	public ApplyReadDto getApplyByStudentNumber(String studentNumber) {
		Student student = studentService.findStudentByStudentNumber(studentNumber);
		Apply apply = applyRepository.findByStudent(student)
			.orElseThrow(ApplyNotFoundException::new);
		return ApplyReadDto.fromEntity(apply, student);
	}

	/**
	 * 신청 상태를 업데이트합니다.
	 *
	 * @param apply 업데이트할 신청 엔티티
	 * @param status 새로운 신청 상태
	 */
	@Transactional
	public void updateApplyStatus(Apply apply, ApplyStatus status) {
		apply.updateStatus(status);
		applyRepository.save(apply);
	}

	/**
	 * 학번과 신청 폼을 기반으로 신청을 조회합니다.
	 *
	 * @param student 학생 엔티티
	 * @param applyForm 신청 폼 엔티티
	 * @return 조회된 신청 엔티티
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 * @throws ApplyDuplicatedException "APPLY_DUPLICATED"
	 */
	public Apply getApplyByStudentNumberAndApplyFormWhenStatusIsApply(Student student, ApplyForm applyForm) {
		if (!existsApplyByStatus(student, applyForm, ApplyStatus.APPLY))
			throw new ApplyNotFoundException();

		return applyRepository.findByStudentAndApplyFormAndStatus(student, applyForm, ApplyStatus.APPLY)
			.orElseThrow(ApplyNotFoundException::new);
	}

	/**
	 * 신청 폼과 상태를 기반으로 모든 신청을 조회합니다.
	 *
	 * @param applyForm 신청 폼 엔티티
	 * @param status 신청 상태
	 * @return 조회된 신청 리스트
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	public List<Apply> getAppliesByApplyFormAndStatus(ApplyForm applyForm, ApplyStatus status) {
		List<Apply> applies = applyRepository.findAllByApplyFormAndStatus(applyForm, status);
		if (applies.isEmpty()) throw new ApplyNotFoundException();
		return applies;
	}

	/**
	 * 특정 학생과 신청 폼에 대한 APPLY 상태의 신청이 있는지 확인합니다.
	 *
	 * @param student 학생 엔티티
	 * @param applyForm 신청 폼 엔티티
	 * @return APPLY 상태의 신청 수
	 */
	private boolean existsApplyByStatus(Student student, ApplyForm applyForm, ApplyStatus applyStatus) {
		return applyRepository.existsByStudentAndApplyFormAndStatus(student, applyForm, applyStatus);
	}

	public List<ApplyReadDto> getAppliesByYearAndSemester(Integer year, Integer semester) {
		ApplyForm applyForm = applyFormService.getApplyFormByYearAndSemester(year, semester);
		return getApplies(applyForm);
	}

	public List<ApplyReadDto> getAppliesNow() {
		ApplyForm applyForm = applyFormService.getActiveApplyForm();
		return getApplies(applyForm);
	}

	private List<ApplyReadDto> getApplies(ApplyForm applyForm) {
		List<Apply> applies = applyRepository.findAllByApplyForm(applyForm);
		return applies.stream()
			.map(apply -> ApplyReadDto.fromEntity(apply, apply.getStudent()))
			.collect(Collectors.toList());
	}

	/**
	 * 특정 년도와 학기, 상태에 해당하는 신청을 조회합니다.
	 *
	 * @param year 년도
	 * @param semester 학기
	 * @param status 신청 상태
	 * @return 조회된 신청 리스트
	 * @throws ApplyFormNotFoundException "APPLY_FORM_NOT_FOUND"
	 */
	public List<ApplyReadDto> getAppliesByYearSemesterAndStatus(Integer year, Integer semester, ApplyStatus status) {
		ApplyForm applyForm = applyFormService.getApplyFormByYearAndSemester(year, semester);
		return getAppliesByStatus(applyForm, status);
	}

	/**
	 * 특정 년도와 학기, 상태에 해당하는 신청을 조회합니다.
	 *
	 * @param status 신청 상태
	 * @return 조회된 신청 리스트
	 * @throws ApplyFormNotFoundException "APPLY_FORM_NOT_FOUND"
	 */
	public List<ApplyReadDto> getAppliesNowByStatus(ApplyStatus status) {
		ApplyForm applyForm = applyFormService.getActiveApplyForm();
		return getAppliesByStatus(applyForm, status);
	}

	private List<ApplyReadDto> getAppliesByStatus(ApplyForm applyForm, ApplyStatus status) {
		List<Apply> applies = applyRepository.findAllByApplyFormAndStatus(applyForm, status);
		return applies.stream()
			.map(apply -> ApplyReadDto.fromEntity(apply, apply.getStudent()))
			.collect(Collectors.toList());
	}


	/**
	 * 특정 년도와 학기에 해당하는 신청과 신고 기록을 조회합니다.
	 *
	 * @param year 년도
	 * @param semester 학기
	 * @return 조회된 신청과 보고서 리스트
	 * @throws ReportNotFoundException "REPORT_NOT_FOUND"
	 * @throws ApplyFormNotFoundException "APPLY_FORM_NOT_FOUND"
	 */
	public List<ApplyReportReadDto> getAppliesAndReportsByYearAndSemester(Integer year, Integer semester) {
		ApplyForm applyForm = applyFormService.getApplyFormByYearAndSemester(year, semester);
		return getAppliesAndReportsByApplyForm(applyForm);
	}


	/**
	 * 현재 활성화된 신청 폼에 해당하는 신청과 신고 기록을 조회합니다.
	 *
	 * @throws ReportNotFoundException "REPORT_NOT_FOUND"
	 * @throws ApplyFormNotFoundException "APPLY_FORM_NOT_FOUND"
	 * @return 조회된 신청과 보고서 리스트
	 */
	public List<ApplyReportReadDto> getAppliesAndReportsNow() {
		ApplyForm applyForm = applyFormService.getActiveApplyForm();
		return getAppliesAndReportsByApplyForm(applyForm);
	}


	private List<ApplyReportReadDto> getAppliesAndReportsByApplyForm(ApplyForm applyForm) {
		List<Apply> applies = applyRepository.findAllByApplyFormAndStatus(applyForm, ApplyStatus.BROKEN_APPLY);
		return applies.stream()
			.map(apply -> {
				Report report = reportService.getReportByApply(apply);
				return ApplyReportReadDto.of(ApplyReadDto.fromEntity(apply, apply.getStudent()), report);
			})
			.collect(Collectors.toList());
	}


	@Transactional
	public ApplyStatus updateReportStatus(ReportStatusUpdateDto updateDto) {
		Apply apply = getApplyById(updateDto.reportId());
		if (apply.getStatus() != ApplyStatus.BROKEN_APPLY)
			throw new ApplyNotFoundException();

		apply.updateStatus(
			updateDto.isApproved() ? ApplyStatus.APPROVE : ApplyStatus.REJECT
		);
		applyRepository.save(apply);

		return apply.getStatus();
	}

	/**
	 * 특정 학생의 신청을 업데이트합니다.
	 *
	 * @param year 년도
	 * @param semester 학기
	 * @param studentNumber 학생 학번
	 * @param requestBody 업데이트할 신청 DTO
	 * @return 업데이트된 신청을 나타내는 DTO
	 * @throws ApplyFormNotFoundException "APPLY_FORM_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 */
	@Transactional
	public ApplyReadDto updateApplyByStudentNumber(Integer year, Integer semester, String studentNumber, ApplyUpdateDto requestBody) {
		ApplyForm applyForm = applyFormService.getApplyFormByYearAndSemester(year, semester);
		Student student = studentService.findStudentByStudentNumber(studentNumber);

		Apply apply = applyRepository.findByStudentAndApplyForm(student, applyForm)
			.orElseThrow(ApplyNotFoundException::new);

		apply.update(requestBody);
		applyRepository.save(apply);

		return ApplyReadDto.fromEntity(apply, student);
	}

	/**
	 * 특정 학생의 신청을 삭제합니다.
	 *
	 * @param year 년도
	 * @param semester 학기
	 * @param studentNumber 학생 학번
	 * @throws ApplyFormNotFoundException "APPLY_FORM_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	@Transactional
	public void deleteApplyByStudentNumber(Integer year, Integer semester, String studentNumber) {
		ApplyForm applyForm = applyFormService.getApplyFormByYearAndSemester(year, semester);
		Student student = studentService.findStudentByStudentNumber(studentNumber);

		Apply apply = applyRepository.findByStudentAndApplyForm(student, applyForm)
			.orElseThrow(ApplyNotFoundException::new);

		applyRepository.delete(apply);
	}

	/**
	 * 특정 신청을 업데이트합니다.
	 *
	 * @param applyId 신청 식별자
	 * @param requestBody 업데이트할 신청 DTO
	 * @return 업데이트된 신청을 나타내는 DTO
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	@Transactional
	public ApplyReadDto updateApplyById(Long applyId, ApplyUpdateDto requestBody) {
		Apply apply = getApplyById(applyId);

		apply.update(requestBody);
		applyRepository.save(apply);

		return ApplyReadDto.fromEntity(apply, apply.getStudent());
	}

	/**
	 * 특정 신청을 삭제합니다.
	 *
	 * @param applyId 신청 식별자
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	@Transactional
	public void deleteApplyById(Long applyId) {
		Apply apply = getApplyById(applyId);

		applyRepository.delete(apply);
	}

	public Apply getApplyById(Long applyId) {
		return applyRepository.findById(applyId)
			.orElseThrow(ApplyNotFoundException::new);
	}

	public Apply getApplyByIdWithStudent(Long applyId) {
		return applyRepository.findByIdWithStudent(applyId)
			.orElseThrow(ApplyNotFoundException::new);
	}
}
