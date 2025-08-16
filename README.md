# FreeFire Tournament Management System

A full-stack application for managing FreeFire tournaments with JWT authentication and role-based access control.

## Features

### Authentication
- **JWT-based authentication** with secure token storage
- **Role-based access control** (Admin and User roles)
- **Protected routes** based on user authentication and roles
- **Redux state management** for authentication state

### User Features
- User registration and login
- User dashboard with profile information
- Tournament participation
- Match history tracking
- Leaderboard viewing

### Admin Features
- Admin dashboard with management tools
- Tournament creation and management
- User management
- Match result updates
- System settings and configuration
- Reports and analytics

## Technology Stack

### Backend
- **Spring Boot 3.5.4** - Main framework
- **Spring Security** - Authentication and authorization
- **JWT (JSON Web Tokens)** - Token-based authentication
- **Spring Data JPA** - Database operations
- **SQL Server** - Database
- **Maven** - Dependency management

### Frontend
- **React 19.1.1** - UI framework
- **Redux Toolkit** - State management
- **React Router** - Client-side routing
- **Axios** - HTTP client
- **CSS3** - Styling

## Project Structure

```
freefire-backend/
├── demo/
│   ├── src/main/java/com/example/demo/
│   │   ├── config/           # Security and JWT configuration
│   │   ├── controller/       # REST API controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── entity/          # JPA entities
│   │   ├── repository/      # Data repositories
│   │   └── service/         # Business logic services
│   └── src/main/resources/
│       └── application.properties

freefire-frontend/
├── src/
│   ├── components/
│   │   ├── Auth/           # Login and Register components
│   │   └── Dashboard/      # User and Admin dashboards
│   ├── store/              # Redux store and slices
│   ├── utils/              # API utilities and interceptors
│   └── App.jsx             # Main application component
```

## Setup Instructions

### Prerequisites
- Java 21
- Node.js (v18 or higher)
- SQL Server
- Maven

### Backend Setup

1. **Navigate to the backend directory:**
   ```bash
   cd "d:\Torunment project\freefire-backend\demo"
   ```

2. Configure environment variables instead of hardcoding secrets.
   Create `freefire-backend/demo/.env.local` from `.env.example` and set:
   - DB_URL, DB_USER, DB_PASSWORD
   - EMAIL_USERNAME, EMAIL_PASSWORD
   - JWT_SECRET, JWT_EXPIRATION
   - APP_CORS_ALLOWED_ORIGINS (e.g., https://primearena.live,https://www.primearena.live,http://localhost:5173)
   - APP_ADMIN_EMAILS

3. **Install dependencies and run:**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

   The backend will start on `http://localhost:8082` per `application.properties`.

### Frontend Setup

1. **Navigate to the frontend directory:**
   ```bash
   cd "d:\Torunment project\freefire-frontend"
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Start the development server:**
   ```bash
   npm run dev
   ```

   The frontend will start on `http://localhost:5174`

## API Endpoints

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/register` - User registration

### Protected Routes
- User routes: `/api/user/**` (requires USER role)
- Admin routes: `/api/admin/**` (requires ADMIN role)

## Usage

### Registration
1. Visit `http://localhost:5174/register`
2. Fill in the registration form
3. Select role (User or Admin)
4. Submit to create account

### Login
1. Visit `http://localhost:5174/login`
2. Enter username and password
3. Login to access role-specific dashboard

### Dashboards
- **User Dashboard** (`/user/dashboard`): Access to tournaments, matches, and profile
- **Admin Dashboard** (`/admin/dashboard`): Management tools for tournaments, users, and system settings

## Security Features

- **JWT tokens** with configurable expiration
- **Password encryption** using BCrypt
- **CORS configuration** for cross-origin requests
- **Request/Response interceptors** for token management
- **Automatic token refresh** and logout on expiration
- **Role-based route protection**

## Database Schema

The application uses the following main entities:
- **User**: Stores user information with roles (USER/ADMIN)
- **Tournament**: Tournament details and management
- **Match**: Individual match information
- **Participation**: User tournament participation

## Development

### Adding New Features
1. **Backend**: Add new controllers, services, and entities as needed
2. **Frontend**: Create new components and add routes in App.jsx
3. **Authentication**: Use the existing JWT infrastructure

### Environment Variables
Configure the following in `application.properties`:
- `app.jwt.secret`: JWT signing secret
- `app.jwt.expiration`: Token expiration time in milliseconds
- Database connection details

## Troubleshooting

### Common Issues
1. **Database Connection**: Ensure SQL Server is running and credentials are correct
2. **CORS Errors**: Check CORS configuration in SecurityConfig.java
3. **Token Expiration**: Tokens expire after 24 hours by default
4. **Port Conflicts**: Frontend and backend use different ports (5174 and 8080)

### Logs
- Backend logs: Check console output when running Spring Boot
- Frontend logs: Check browser developer console

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is for educational purposes and tournament management.
