package com.epam.finaltask.service;

import java.util.UUID;

import com.epam.finaltask.dto.UserDTO;
import com.epam.finaltask.mapper.UserMapper;
import com.epam.finaltask.model.User;
import com.epam.finaltask.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;

	@Override
	public UserDTO register(UserDTO userDTO) {
		User saved = userRepository.save(userMapper.toUser(userDTO));
		return userMapper.toUserDTO(saved);
	}

	@Override
	public UserDTO updateUser(String username, UserDTO userDTO) {
		User updated =
				getUserByUsername(username) != null ?
				userRepository.save(userMapper.toUser(userDTO)) : null;
		return userMapper.toUserDTO(updated);
	}

	@Override
	public UserDTO getUserByUsername(String username) {
		return userRepository.findUserByUsername(username).map(userMapper::toUserDTO).orElse(null);
	}

	@Override
	public UserDTO changeAccountStatus(UserDTO userDTO) {
		UserDTO user = getUserById(UUID.fromString(userDTO.getId()));
		if (user != null /*&& user.isActive() != userDTO.isActive()*/) {
			user = userMapper.toUserDTO(
					userRepository.save(userMapper.toUser(userDTO))
			);
		}
		return user;
	}

	@Override
	public UserDTO getUserById(UUID id) {
		return userRepository.findById(id).map(userMapper::toUserDTO).orElse(null);
	}

	@Override
	public boolean existsByUsernameOrEmail(String username, String email) {
		return userRepository.existsByUsernameOrEmail(username, email);
	}

}
