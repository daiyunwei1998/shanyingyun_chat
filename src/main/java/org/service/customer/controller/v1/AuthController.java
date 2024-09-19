package org.service.customer.controller.v1;

import jakarta.validation.Valid;
import org.service.customer.dto.api.ResponseDto;
import org.service.customer.dto.auth.RegisterRequest;
import org.service.customer.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        String tenantId = registerRequest.getTenantId();
        try {
            // Call the AuthService to register the user
            authService.registerUser(registerRequest);

            // Return a 201 Created response with a success message
            return new ResponseEntity<>(new ResponseDto("User registered successfully for tenant " + tenantId), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            // Return a 400 Bad Request if there's an invalid input
            e.printStackTrace();
            return new ResponseEntity<>(new ResponseDto("Invalid input: " + e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
}
