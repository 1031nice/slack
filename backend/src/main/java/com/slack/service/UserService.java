package com.slack.service;

import com.slack.domain.user.User;
import com.slack.exception.UserNotFoundException;
import com.slack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * User Domain Service
 * 
 * User 도메인에 대한 비즈니스 로직을 처리합니다.
 * 다른 Domain Service와의 의존성을 제거하여 순환 참조를 방지합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * authUserId로 User를 찾습니다.
     * 사용자가 없으면 예외를 발생시킵니다.
     * 
     * @param authUserId 인증 서버의 사용자 ID
     * @return User
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    public User findByAuthUserId(String authUserId) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found with authUserId: " + authUserId));
    }

    /**
     * authUserId로 User를 찾습니다.
     * 사용자가 없으면 Optional.empty()를 반환합니다.
     * 
     * @param authUserId 인증 서버의 사용자 ID
     * @return Optional<User>
     */
    public Optional<User> findByAuthUserIdOptional(String authUserId) {
        return userRepository.findByAuthUserId(authUserId);
    }

    /**
     * ID로 User를 찾습니다.
     * 
     * @param id User ID
     * @return User
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    /**
     * 새 User를 생성합니다.
     * 
     * @param authUserId 인증 서버의 사용자 ID
     * @param email 사용자 이메일
     * @param name 사용자 이름
     * @return 생성된 User
     */
    @Transactional
    public User createUser(String authUserId, String email, String name) {
        User newUser = User.builder()
                .authUserId(authUserId)
                .email(email)
                .name(name)
                .build();
        return userRepository.save(newUser);
    }
}

