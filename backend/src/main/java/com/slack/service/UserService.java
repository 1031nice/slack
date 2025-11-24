package com.slack.service;

import com.slack.domain.user.User;
import com.slack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * authUserId로 User를 찾거나, 없으면 생성합니다.
     * JWT의 sub 클레임과 email, name을 사용합니다.
     */
    @Transactional
    public User findOrCreateByAuthUserId(String authUserId, String email, String name) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .authUserId(authUserId)
                            .email(email)
                            .name(name)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    public User findByAuthUserId(String authUserId) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new RuntimeException("User not found with authUserId: " + authUserId));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }
}

