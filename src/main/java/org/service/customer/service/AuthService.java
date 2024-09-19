package org.service.customer.service;

import org.service.customer.dto.auth.RegisterRequest;
import org.service.customer.exceptions.auth.InvalidRegisterInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.JDBCType;
import java.sql.Timestamp;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TenantTableService tenantTableService;

    public AuthService(JdbcTemplate jdbcTemplate, BCryptPasswordEncoder bCryptPasswordEncoder, TenantTableService tenantTableService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = bCryptPasswordEncoder;
        this.tenantTableService = tenantTableService;
    }

    public void registerUser(RegisterRequest registerRequest) {
        String tenantId = registerRequest.getTenantId();
        String name = registerRequest.getName();
        String rawPassword = registerRequest.getPassword();
        String email = registerRequest.getEmail();
        String role = registerRequest.getRole();

        tenantTableService.createTenantUserTable(tenantId);

        // Hash the password
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Create tenant-specific table name
        String tableName = tenantId + "_users";

        try {
            // Prepare the SQL query to insert the user into the specific tenant table
            String sql = String.format("INSERT INTO %s (name, password, email, registered_time, signed_time, role, tenant_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)", tableName);

            // Execute the query using JdbcTemplate
            jdbcTemplate.update(sql,
                    name,
                    encodedPassword,
                    email,
                    new Timestamp(System.currentTimeMillis()), // registered_time
                    new Timestamp(System.currentTimeMillis()), // signed_time
                    role,
                    tenantId
            );
        } catch (DuplicateKeyException e) {
            // Handle duplicate key exception
            throw new InvalidRegisterInfo("A user with this email already exists!");
        } catch (DataIntegrityViolationException e) {
            // Handle any other data integrity issues
            throw new InvalidRegisterInfo("Data integrity violation occurred: " + e.getMessage());
        } catch (Exception e) {
            // Handle any other exceptions
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage());
        }

    }

    // Update the username of an existing user by user ID
    public void updateUsername(String tenantId, Long userId, String newName) {
        // Create tenant-specific table name
        String tableName = tenantId + "_users";

        // Prepare the SQL query to update the username
        String sql = String.format("UPDATE %s SET name = ? WHERE id = ?", tableName);

        // Execute the update using JdbcTemplate
        jdbcTemplate.update(sql, newName, userId);
    }
}
