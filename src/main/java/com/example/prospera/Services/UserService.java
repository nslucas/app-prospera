package com.example.prospera.Services;

import com.example.prospera.DTO.UserRecord;
import com.example.prospera.Entities.User;
import com.example.prospera.Exceptions.ObjectNotFoundException;
import com.example.prospera.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private static final String CONNECTION_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CONNECTION_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository repository;
    private final AuthenticatedUserService authenticatedUserService;

    public UserService(UserRepository repository, AuthenticatedUserService authenticatedUserService) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public User findById(Integer id) {
        Optional<User> optionalUser = repository.findById(id);
        return optionalUser.orElseThrow(() -> new ObjectNotFoundException("Object not found!"));
    }

    public User findAuthenticatedUserById(Integer id) {
        return authenticatedUserService.requireAuthenticatedUser(id);
    }

    public void delete(Integer id){
        findById(id);
        repository.deleteById(id);
    }

    public void deleteAuthenticatedUser(Integer id) {
        User authenticatedUser = authenticatedUserService.requireAuthenticatedUser(id);
        repository.deleteById(authenticatedUser.getId());
    }

    public User update(User obj){
        User newObj = repository.getReferenceById(obj.getId());
        updateData(newObj, obj);
        return repository.save(newObj);
    }

    public User updateAuthenticatedUser(User obj) {
        authenticatedUserService.requireAuthenticatedUser(obj.getId());
        return update(obj);
    }

    private void updateData(User newObj, User obj) {
        newObj.setName(obj.getName());
        newObj.setLastName(obj.getLastName());
        newObj.setMonthLimit(obj.getMonthLimit());
        newObj.setEmail(obj.getEmail());
    }

    public User fromDTO(UserRecord objDTO){
        return new User(objDTO.name(), objDTO.lastName(), objDTO.monthLimit(), objDTO.email());
    }

    public String generateUniqueConnectionCode() {
        String code;
        do {
            code = randomConnectionCode();
        } while (repository.existsByConnectionCode(code));
        return code;
    }

    private String randomConnectionCode() {
        StringBuilder builder = new StringBuilder(CONNECTION_CODE_LENGTH);
        for (int i = 0; i < CONNECTION_CODE_LENGTH; i++) {
            builder.append(CONNECTION_CODE_CHARS.charAt(RANDOM.nextInt(CONNECTION_CODE_CHARS.length())));
        }
        return builder.toString();
    }

}
