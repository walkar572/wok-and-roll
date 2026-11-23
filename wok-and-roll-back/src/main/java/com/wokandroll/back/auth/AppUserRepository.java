package com.wokandroll.back.auth;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Users and roles. Plain SQL with named parameters (JdbcClient), like pricing and catalog.detail.
 */
@Repository
public class AppUserRepository {

  /** One row of app_user with its role code. */
  public record UserRow(
      long id, String email, String passwordHash, String fullName, String phone, String role) {}

  private static final String SELECT =
      """
      SELECT u.id, u.email, u.password_hash, u.full_name, u.phone, r.code AS role
      FROM app_user u
      JOIN role r ON r.id = u.role_id
      """;

  private final JdbcClient jdbc;

  public AppUserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean existsByEmail(String email) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM app_user WHERE email = :email)")
        .param("email", email)
        .query(Boolean.class)
        .single();
  }

  public Optional<UserRow> findByEmail(String email) {
    return jdbc.sql(SELECT + "WHERE u.email = :email")
        .param("email", email)
        .query(this::map)
        .optional();
  }

  public Optional<UserRow> findById(long id) {
    return jdbc.sql(SELECT + "WHERE u.id = :id").param("id", id).query(this::map).optional();
  }

  /** Saves a new user with the given role and returns its id. */
  public long insert(
      String email, String passwordHash, String fullName, String phone, String role) {
    return jdbc.sql(
            """
            INSERT INTO app_user (email, password_hash, full_name, phone, role_id)
            VALUES (:email, :hash, :fullName, :phone, (SELECT id FROM role WHERE code = :role))
            RETURNING id
            """)
        .param("email", email)
        .param("hash", passwordHash)
        .param("fullName", fullName)
        .param("phone", phone)
        .param("role", role)
        .query(Long.class)
        .single();
  }

  private UserRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new UserRow(
        rs.getLong("id"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getString("full_name"),
        rs.getString("phone"),
        rs.getString("role"));
  }
}
