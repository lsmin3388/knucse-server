package com.knucse.authentication.application.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.knucse.authentication.application.entity.OAuthUserInfo;

public interface OAuth2UserInfoRepository extends JpaRepository<OAuthUserInfo, Long> {
	@EntityGraph(attributePaths = "student")
	Optional<OAuthUserInfo> findByEmail(String email);
	boolean existsByEmail(String email);
}
