# Auth and roles design

Refs #35 · Owner: Nodir · Design only, the code comes in #62 (register and login), #63 (my orders) and #65 (RBAC).
Based on: Project Plan v2, `docs/decision-log.md`, `docs/architecture.md` sections 4 and 7.

## 1. Stack

| What | Choice |
|---|---|
| Framework | Spring Security (comes with Spring Boot) |
| Token | JWT, signed with HS256 |
| Secret | env `JWT_SECRET`, at least 32 characters, never in the code or in git |
| Token life | 24 hours (env `JWT_TTL_HOURS`, default 24) |
| Passwords | BCrypt (`BCryptPasswordEncoder`, strength 10) |
| Sessions | none, the API is stateless (`SessionCreationPolicy.STATELESS`), CSRF off because we do not use cookies |

No refresh token. After 24 hours the user logs in again. This is enough for the project and keeps the code small.

## 2. Roles

| Role | Who | Can do |
|---|---|---|
| CUSTOMER | a guest who registered | everything a guest can, plus own profile and own order history |
| VIEWER | manager who only looks at numbers | reports |
| STAFF | cafe staff | reserved for later (orders and bookings of a venue), for now same rights as CUSTOMER |
| ADMIN | admin of the chain | admin API (catalog, prices, availability) and reports |

One role per user. Register always gives CUSTOMER. ADMIN, STAFF and VIEWER users come from a seed migration, there is no public way to become admin.

## 3. Who can open what

| Path | Access |
|---|---|
| `/api/v1/auth/**` | public |
| `/api/health`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` | public |
| Guest flow: `GET /api/v1/countries`, `/categories`, `/products/**`, `/pickup-points`, `/delivery-zones`, `/time-slots`, `POST /api/v1/cart/quote`, `POST /api/v1/orders`, `POST /api/v1/bookings` | public |
| `/api/v1/me/**` | any logged-in user |
| `/api/v1/reports/**` | ADMIN or VIEWER |
| `/api/v1/admin/**` | ADMIN |
| anything else | denied by default |

A guest can order without an account. If the request to `POST /api/v1/orders` has a valid token, the order gets the `user_id` of that user, so it shows up in "my orders" (#63). Without a token `user_id` stays empty.

## 4. Endpoints

### POST /api/v1/auth/register

```json
// request
{ "email": "aziz@mail.uz", "password": "secret123", "fullName": "Aziz Karimov", "phone": "+998901234567" }
// 201 response
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "role": "CUSTOMER" }
```

Validation: email is a valid email, password 8 or more characters, fullName required, phone optional.
Email is saved in lower case, so `Aziz@Mail.uz` and `aziz@mail.uz` are the same user.

### POST /api/v1/auth/login

```json
// request
{ "email": "aziz@mail.uz", "password": "secret123" }
// 200 response
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "role": "CUSTOMER" }
```

### GET /api/v1/me

Returns `{ "id", "email", "fullName", "phone", "role" }` of the logged-in user. Useful for the frontend after a page reload.

The token is sent in every request as `Authorization: Bearer <token>`.

## 5. Token content

```json
{ "sub": "15", "email": "aziz@mail.uz", "role": "CUSTOMER", "iat": 1762400000, "exp": 1762486400 }
```

`sub` is the user id. The role is in the token, so the filter does not go to the database on every request. If an admin changes someone's role, it works after the old token expires (max 24 hours). For this project that is fine.

## 6. Errors

Same error body as the rest of the API: `{ "code", "message", "fields" }`.

| Case | Status | code |
|---|---|---|
| Wrong email or password (same message for both, so nobody can check which emails exist) | 401 | `INVALID_CREDENTIALS` |
| No token on a closed path, or a bad or expired token on any path except `POST /auth/login` and `/auth/register` (they ignore the token); header `WWW-Authenticate: Bearer` (#151) | 401 | `UNAUTHORIZED` |
| Token is fine but the role is not enough | 403 | `FORBIDDEN` |
| Email already registered | 409 | `EMAIL_TAKEN` |
| Validation (bad email, short password) | 400 | `VALIDATION_ERROR` |

401 and 403 come from Spring Security before the controller, so we add a small `AuthenticationEntryPoint` and `AccessDeniedHandler` that write this JSON. Otherwise Spring returns its own HTML or empty body.

## 7. Tables

`user` and `order` are reserved words in PostgreSQL, so the tables are `app_user` and `role` (see architecture section 4).

```sql
-- V<date>_<time>__auth_tables.sql (timestamp version, after V1 from #32)
create table role (
  id   bigserial primary key,
  code varchar(20) not null unique          -- ADMIN, STAFF, VIEWER, CUSTOMER
);

create table app_user (
  id            bigserial primary key,
  email         varchar(255) not null unique, -- saved in lower case
  password_hash varchar(100) not null,        -- BCrypt hash, never the password
  full_name     varchar(150) not null,
  phone         varchar(30),
  role_id       bigint not null references role(id),
  created_at    timestamptz not null default now()
);

insert into role (code) values ('ADMIN'), ('STAFF'), ('VIEWER'), ('CUSTOMER');
```

`customer_order.user_id` (nullable, references `app_user`) is added by the owner of the order table, so this migration does not touch orders.

Demo users for ADMIN, STAFF and VIEWER go into a separate seed migration with BCrypt hashes. Their passwords are written in the backend README for the demo, not in the code.

## 8. Code layout (for #62 and #65)

```
com.wokandroll.back.auth
  config/SecurityConfig          paths and roles from section 3, stateless, BCrypt bean
  jwt/JwtService                 create and check tokens, reads JWT_SECRET and JWT_TTL_HOURS
  jwt/JwtAuthFilter              reads "Authorization: Bearer", puts the user into the SecurityContext
  controller/AuthController      /api/v1/auth/register, /api/v1/auth/login
  service/AuthService            register, login, password check
  repository/AppUserRepository, RoleRepository
  dto/RegisterRequest, LoginRequest, AuthResponse
com.wokandroll.back.account
  controller/MeController        /api/v1/me, /api/v1/me/orders (#63)
```

JWT library: `io.jsonwebtoken:jjwt` (api, impl, jackson). It is the most common one and has good docs.

## 9. Environment variables

| Name | Example | Note |
|---|---|---|
| `JWT_SECRET` | any random string, 32+ characters | required, the app does not start without it |
| `JWT_TTL_HOURS` | `24` | optional |

In `docker-compose.yml` the secret comes from `${JWT_SECRET}` in a local `.env` file that is in `.gitignore`. A `.env.example` with a placeholder goes into the repo.

## 10. Swagger

Swagger gets a "Bearer" security scheme, so the Authorize button in Swagger UI takes a token and closed endpoints can be tested from the browser.

## 11. How we test it (for QA and #62)

1. Register, get a token with role CUSTOMER.
2. Register again with the same email: 409 `EMAIL_TAKEN`.
3. Login with a wrong password: 401 `INVALID_CREDENTIALS`.
4. `GET /api/v1/me` without a token: 401. With the token: 200.
5. `GET /api/v1/reports/sales-by-region` as CUSTOMER: 403. As VIEWER or ADMIN: 200.
6. `GET /api/v1/admin/...` as VIEWER: 403. As ADMIN: 200.
7. Guest flow (menu, cart quote, order) without a token: 200 or 201.

## Open questions

1. Does STAFF need its own rights in this project (for example, see today's orders of a venue), or do we keep it as a reserved role?
2. Do we show "Log in" in the header from Sprint 2, or only in Stage 4 together with order history?
