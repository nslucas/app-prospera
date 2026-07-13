# Prospera API

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![MySQL](https://img.shields.io/badge/mysql-%23316192.svg?style=for-the-badge&logo=mysql&logoColor=white)

This project is an API built using **Java, Spring Boot, Flyway Migrations, MySQL as the database, and Spring Security for authentication control.** 
The API was developed to concretize the knowledge accquired in my 8 months journey of learning Java, and some concepts as well,
like Oriented Object Programming,Spring Annotations,Databases/SQL, code versioning with Git, and finally API REST. 

The goal of this API is to help people take control of their financial life. Users can register themselves into the app, and logged-in users can read, create, update and delete expenses.
As someone who has used other finance apps before, I missed some features in the apps, such as: the system should provide a monthly and yearly balance (projection). 
The user can set a monthly spending limit. The user can add cards by specifying the bank, card network, and card name to avoid using sensitive data. Among other things.

DISCLAIMER: currently the API can only read, update, register, and delete users and expenses, I am currently working to improve the codebase as well as implementing the features mentioned above.  
## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
- [API Endpoints](#api-endpoints)
- [Database](#database)
- [Contributing](#contributing)

## Installation

1. Clone the repository:

```bash
git clone https://github.com/nslucas/prospera.git
```

2. Install dependencies with Maven

3. Install [MySQL](https://www.mysql.com/downloads/)

## Usage

1. Make sure that MySQL service is running, if it is not, start the service in Task Explorer -> Services -> MySQL80
2. The API will be accessible at http://localhost:8080

Interactive API documentation is available at http://localhost:8080/swagger-ui.html. The raw OpenAPI JSON is available at http://localhost:8080/v3/api-docs.

To test protected endpoints in Swagger UI, call `POST /auth/login`, copy the returned JWT, select **Authorize**, and enter the token. Swagger UI adds the `Bearer` prefix automatically.

## Running with Docker Compose

The application can also run with Docker Compose. The API and MySQL database are started in separate containers.

Create a local `.env` file from the example and replace every secret before starting the stack:

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Services exposed locally:

- API: http://localhost:8080

By default, the API is bound to `127.0.0.1:8080`, so it is reachable from the VPS itself and from a local reverse proxy, but not directly exposed on the public network interface. MySQL is not published to the host; only the API container can reach it through the internal Docker hostname `db`.

Authentication rate limits use the original client IP supplied by the local reverse proxy. The proxy must
overwrite incoming `Forwarded` and `X-Forwarded-*` headers rather than appending values supplied by the
public client. Keep the API bound to localhost when `FORWARD_HEADERS_STRATEGY=native` is enabled.

The default authentication limits are 30 login attempts per IP every 15 minutes, 5 failed login attempts
per email every 15 minutes, 5 registrations per IP per hour, and 3 registrations per email per day. These
values and the in-memory cache size can be overridden with the `AUTH_*` variables in `.env.example`.

The production domain `appprospera.com.br` is allowed by the default CORS configuration, including `www.appprospera.com.br` and `api.appprospera.com.br`. To override the allowed origins, set `CORS_ALLOWED_ORIGIN_PATTERNS` with a comma-separated list before starting Compose.

JWT sessions use `JWT_EXPIRATION_HOURS`, defaulting to 720 hours. Set a lower value in `.env` if the deployment should require users to sign in more frequently.

To stop the containers:

```bash
docker compose down
```

To stop the containers and remove the persisted database volume:

```bash
docker compose down -v
```


## API Endpoints
The API provides the following endpoints:

```markdown
GET /users - Retrieve a list of all users.

GET /users/{userId} - Retrieve a specific user by ID.

POST /users - Register a new user.

PUT /users/{userId} - Update an already registered user.

DELETE /users/{userId}

GET /expenses - Retrieve all expenses registered into the API.

GET /expenses/{expenseId} - Retrieve a specific expense by ID.

POST /expenses - Register a new expense.

DELETE /expenses/{expenseId}

```


## Database
The project utilizes [MySQL](https://www.mysql.com/) as the database. The necessary database migrations are managed using Flyway.


## Contributing

Contributions are welcome! If you find any issues or have suggestions for improvements, please open an issue or submit a pull request to the repository.

When contributing to this project, please follow the existing code style, [commit conventions](https://www.conventionalcommits.org/en/v1.0.0/), and submit your changes in a separate branch.

## License

This project is licensed under the [MIT License](LICENSE).
