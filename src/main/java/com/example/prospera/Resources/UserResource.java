package com.example.prospera.Resources;

import com.example.prospera.DTO.UserRecord;
import com.example.prospera.Entities.User;
import com.example.prospera.Services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserResource {

    @Autowired
    private UserService service;

    @GetMapping
    public ResponseEntity<List<UserRecord>> findAll(){
        List<UserRecord> list = service.findAll().stream().map(UserRecord::new).toList();
        return ResponseEntity.ok().body(list);
    }

    @GetMapping(value="/{id}")
    public ResponseEntity<UserRecord> findById(@PathVariable Integer id) {
        User obj = service.findAuthenticatedUserById(id);
        return ResponseEntity.ok().body(new UserRecord(obj));
    }


    @DeleteMapping(value="/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.deleteAuthenticatedUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value="/{id}")
    public ResponseEntity<User> update(@RequestBody UserRecord objDTO, @PathVariable Integer id) {
        User obj = service.fromDTO(objDTO);
        obj.setId(id);
        obj = service.updateAuthenticatedUser(obj);
        return ResponseEntity.noContent().build();
    }
}
