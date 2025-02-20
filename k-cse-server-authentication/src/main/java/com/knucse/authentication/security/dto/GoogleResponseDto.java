package com.knucse.authentication.security.dto;

import java.util.Map;

import com.knucse.authentication.application.entity.OAuthUserInfo;

public class GoogleResponseDto implements Oauth2ResponseDto {

    private final Map<String, Object> attributes;

    public GoogleResponseDto(Map<String, Object> attribute) {
        this.attributes = attribute;
    }

    @Override
    public String getProvider() {
        return "google";
    }

    @Override
    public String getProviderId() {

        return attributes.get("sub").toString();
    }

    @Override
    public String getEmail() {
        return attributes.get("email").toString();
    }

    @Override
    public String getName() {
        return attributes.get("name").toString();
    }

    @Override
    public OAuthUserInfo toEntity() {
        return OAuthUserInfo.builder()
                .email(getEmail())
                .provider(getProvider())
                .providerId(getProviderId())
                .build();
    }
}
