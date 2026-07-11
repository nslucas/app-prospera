package com.example.prospera.Resources;

import com.example.prospera.DTO.AuthenticationDTO;
import com.example.prospera.DTO.LoginResponseDTO;
import com.example.prospera.DTO.RegisterDTO;
import com.example.prospera.Entities.User;
import com.example.prospera.Entities.UserRole;
import com.example.prospera.Infra.Security.TokenService;
import com.example.prospera.Services.UserService;
import com.example.prospera.repositories.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("auth")
@SecurityRequirements
public class AuthenticationResource {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    UserRepository _repository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Validated AuthenticationDTO data){
        try {
            var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.password());
            var auth = this.authenticationManager.authenticate(usernamePassword);

            User user = (User) auth.getPrincipal();
            var token = tokenService.generateToken(user);
            return ResponseEntity.ok(new LoginResponseDTO(token, user.getId(), user.getEmail(), user.getName(), user.getLastName()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred");
        }
    }

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Validated RegisterDTO data){
        if(this._repository.findByEmail(data.email()) != null){
            return ResponseEntity.badRequest().build();
        }
        String encryptedPassword = BCrypt.hashpw(data.password(), BCrypt.gensalt());
        UserRole role = data.role() == null ? UserRole.USER : data.role();
        User newUser = new User(data.name(), data.lastName(), data.monthLimit(), data.email(), encryptedPassword, role);
        newUser.setConnectionCode(userService.generateUniqueConnectionCode());

        this._repository.save(newUser);

        return ResponseEntity.ok().build();
    }

}
