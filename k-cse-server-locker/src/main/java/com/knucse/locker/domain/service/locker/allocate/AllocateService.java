package com.knucse.locker.domain.service.locker.allocate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.knucse.locker.api.locker.allocate.dto.AllocateReadDto;
import com.knucse.locker.api.locker.apply.dto.ReportStatusUpdateDto;
import com.knucse.locker.domain.exception.locker.allocate.AllocateNotFoundException;
import com.knucse.locker.domain.model.locker.Locker;
import com.knucse.locker.domain.model.locker.allocate.Allocate;
import com.knucse.locker.domain.model.locker.apply.Apply;
import com.knucse.locker.domain.model.locker.apply.ApplyStatus;
import com.knucse.locker.domain.model.locker.applyForm.ApplyForm;
import com.knucse.student.student.model.Student;
import com.knucse.locker.domain.persistence.AllocateRepository;
import com.knucse.locker.domain.service.locker.LockerService;
import com.knucse.locker.domain.service.locker.apply.ApplyService;
import com.knucse.locker.domain.service.locker.applyForm.ApplyFormService;
import com.knucse.student.student.service.StudentService;
import com.knucse.student.dues.service.DuesService;

import knu.univ.cse.server.core.domain.exception.locker.LockerFullNotFoundException;
import knu.univ.cse.server.core.domain.exception.locker.apply.ApplyNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AllocateService {
	/* Internal Dependencies */
	private final AllocateRepository allocateRepository;

	/* External Dependencies */
	private final StudentService studentService;
	private final ApplyService applyService;
	private final LockerService lockerService;
	private final ApplyFormService applyFormService;
	private final DuesService duesService;

	/**
	 * 학번으로 받은 학생의 신청을 특정 사물함에 할당합니다.
	 *
	 * @param studentNumber 학생의 학번
	 * @param lockerName 할당할 사물함의 이름
	 * @return 할당된 사물함의 DTO
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 * @throws LockerNotFoundException "LOCKER_NOT_FOUND"
	 * @throws AllocateDuplicatedException "ALLOCATE_DUPLICATED"
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public AllocateReadDto allocateLockerByStudentNumber(String studentNumber, String lockerName) {
		// 1. 학번으로 학생을 찾는다.
		Student student = studentService.findStudentByStudentNumber(studentNumber);

		// 2. 현재 활성화된 신청폼을 찾는다.
		ApplyForm applyForm = applyFormService.getActiveApplyForm();

		// 3. 현재 활성화된 신청폼에서 해당 학생의 미처리된 신청을 찾는다.
		Apply targetApply = applyService
			.getApplyByStudentNumberAndApplyFormWhenStatusIsApply(student, applyForm);

		// 4. 학생에게 부여할 사물함을 찾고 고장 및 유효성 검사를 한다.
		Locker targetLocker = lockerService.getLockerByLockerName(lockerName, applyForm);

		// 이미 할당된 사물함이 있는 경우, 기존 할당을 삭제한다.
		deleteAllocate(student, applyForm);

		// 5. Allocate 객체를 생성하고 저장한다.
		Allocate allocate = Allocate.builder()
			.applyForm(applyForm)
			.student(student)
			.locker(targetLocker)
			.apply(targetApply)
			.build();

		allocateRepository.save(allocate);

		// 6. 신청 상태를 승인으로 변경한다.
		applyService.updateApplyStatus(targetApply, ApplyStatus.APPROVE);

		// 7. AllocateReadDto 를 반환한다.
		return AllocateReadDto.fromEntity(student, targetApply, applyForm, targetLocker);
	}

	/**
	 * 학번으로 받은 학생의 신청을 랜덤 사물함에 할당합니다.
	 *
	 * @param studentNumber 학생의 학번
	 * @return 할당된 사물함의 DTO
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 * @throws LockerFullNotFoundException "LOCKER_FULL_NOT_FOUND"
	 * @throws AllocateDuplicatedException "ALLOCATE_DUPLICATED"
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public AllocateReadDto allocateRandomLockerByStudentNumber(String studentNumber) {
		// 1. 학번으로 학생을 찾는다.
		Student student = studentService.findStudentByStudentNumber(studentNumber);

		// 2. 현재 활성화된 신청폼을 찾는다.
		ApplyForm applyForm = applyFormService.getActiveApplyForm();

		// 3. 현재 활성화된 신청폼에서 해당 학생의 미처리된 신청을 찾는다.
		Apply targetApply = applyService
			.getApplyByStudentNumberAndApplyFormWhenStatusIsApply(student, applyForm);

		// 4. 학생에게 부여할 랜덤 사물함을 찾고 고장 및 유효성 검사를 한다.
		Locker targetLocker = lockerService.getRandomLocker(targetApply);

		// 이미 할당된 사물함이 있는 경우, 기존 할당을 삭제한다.
		deleteAllocate(student, applyForm);

		// 5. Allocate 객체를 생성하고 저장한다.
		Allocate allocate = Allocate.builder()
			.applyForm(applyForm)
			.student(student)
			.locker(targetLocker)
			.apply(targetApply)
			.build();

		allocateRepository.save(allocate);

		// 6. 신청 상태를 승인으로 변경한다.
		applyService.updateApplyStatus(targetApply, ApplyStatus.APPROVE);

		// 7. AllocateReadDto 를 반환한다.
		return AllocateReadDto.fromEntity(student, targetApply, applyForm, targetLocker);
	}

	/**
	 * 고장 신고 난 학생에게 랜덤 사물함을 할당합니다.
	 *
	 * @param requestBody 신고 ID를 포함한 DTO
	 * @return 할당된 사물함의 DTO
	 * @throws StudentNotFoundException "STUDENT_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 * @throws LockerFullNotFoundException "LOCKER_FULL_NOT_FOUND"
	 * @throws AllocateDuplicatedException "ALLOCATE_DUPLICATED"
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public AllocateReadDto allocateRandomLockerWhenReport(ReportStatusUpdateDto requestBody) {
		// 1. 신고 ID로 신청을 찾는다.
		Apply targetApply = applyService.getApplyByIdWithStudent(requestBody.reportId());

		// 2. 학생을 찾는다.
		Student student = targetApply.getStudent();

		// 3. 현재 활성화된 신청폼을 찾는다.
		ApplyForm applyForm = applyFormService.getActiveApplyForm();

		// 4. 학생에게 부여할 랜덤 사물함을 찾고 고장 및 유효성 검사를 한다.
		Locker targetLocker = lockerService.getRandomLocker(targetApply);

		// 이미 할당된 사물함이 있는 경우, 기존 할당을 삭제한다.
		deleteAllocate(student, applyForm);

		// 5. Allocate 객체를 생성하고 저장한다.
		Allocate allocate = Allocate.builder()
			.applyForm(applyForm)
			.student(student)
			.locker(targetLocker)
			.apply(targetApply)
			.build();

		allocateRepository.save(allocate);

		// 6. 신청 상태를 승인으로 변경한다.
		applyService.updateApplyStatus(targetApply, ApplyStatus.APPROVE);

		// 7. AllocateReadDto 를 반환한다.
		return AllocateReadDto.fromEntity(student, targetApply, applyForm, targetLocker);
	}

	/**
	 * 현재 활성화된 신청 폼의 모든 신청에 대해 랜덤 사물함을 할당합니다.
	 *
	 * @return 할당된 모든 사물함의 DTO 리스트
	 * @throws LockerFullNotFoundException "LOCKER_FULL_NOT_FOUND"
	 * @throws ApplyNotFoundException "APPLY_NOT_FOUND"
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public List<AllocateReadDto> allocateAllLockers() {
		// 1. 현재 활성화된 신청폼을 찾는다.
		ApplyForm applyForm = applyFormService.getActiveApplyForm();

		// 2. 현재 활성화된 신청폼에서 모든 미처리된 신청을 찾는다.
		List<Apply> applies = applyService.getAppliesByApplyFormAndStatus(applyForm, ApplyStatus.APPLY);
		if (applies.isEmpty()) throw new ApplyNotFoundException();

		// 3. 신청별 가중치를 계산하여 Pair 리스트로 저장
		List<ApplyWithWeight> applyWithWeights = new ArrayList<>();
		for (Apply apply : applies) {
			long studentId = apply.getStudent().getId();
			int weight = 0;

			// 회비 납부 여부에 따른 가중치 부여
			if (duesService.isDues(studentId)) {
				weight += 100;
			}

			// todo: 추가적인 가중치 부여 로직

			applyWithWeights.add(new ApplyWithWeight(apply, weight));
		}

		// 4. 가중치가 높은 순으로 정렬
		applyWithWeights.sort(Comparator.comparingInt(ApplyWithWeight::weight).reversed());

		// 5. 정렬된 신청 리스트로부터 AllocateReadDto 리스트 생성
		List<AllocateReadDto> allocateReadDtos = new ArrayList<>();

		for (ApplyWithWeight applyWithWeight : applyWithWeights) {
			Apply apply = applyWithWeight.apply();

			try {
				// 5.1. 학생에게 부여할 랜덤 사물함을 찾고 고장 및 유효성 검사를 한다.
				Locker targetLocker = lockerService.getRandomLocker(apply);

				// 이미 할당된 사물함이 있는 경우, 기존 할당을 삭제한다.
				deleteAllocate(apply.getStudent(), applyForm);

				// 5.2. Allocate 객체를 생성하고 저장한다.
				Allocate allocate = Allocate.builder()
					.applyForm(applyForm)
					.student(apply.getStudent())
					.locker(targetLocker)
					.apply(apply)
					.build();

				allocateRepository.save(allocate);

				// 5.3. 신청 상태를 승인으로 변경한다.
				applyService.updateApplyStatus(apply, ApplyStatus.APPROVE);

				// 5.4. AllocateReadDto 를 생성하여 리스트에 추가한다.
				AllocateReadDto dto = AllocateReadDto.fromEntity(apply.getStudent(), apply, applyForm, targetLocker);
				allocateReadDtos.add(dto);
			} catch (LockerFullNotFoundException e) {
				// 모든 Locker 가 할당되어 있거나 고장 상태인 경우, 더 이상 할당 불가
				throw new LockerFullNotFoundException();
			}
		}

		return allocateReadDtos;
	}

	/**
	 * 신청과 가중치를 함께 저장하기 위한 내부 클래스
	 */
	private record ApplyWithWeight(Apply apply, int weight) { }


	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void deleteAllocate(Student student, ApplyForm applyForm) {
		if (allocateRepository.existsByStudentAndApplyForm(student, applyForm)) {
			allocateRepository.deleteByStudentAndApplyForm(student, applyForm);
		}
	}

	public AllocateReadDto getAllocateForm(String studentNumber) {
		Student student = studentService.findStudentByStudentNumber(studentNumber);
		ApplyForm applyForm = applyFormService.getActiveApplyForm();
		Allocate allocate = allocateRepository.findByStudentAndApplyForm(student, applyForm)
			.orElseThrow(AllocateNotFoundException::new);

		return AllocateReadDto.fromEntity(student, allocate.getApply(), applyForm, allocate.getLocker());
	}
}
