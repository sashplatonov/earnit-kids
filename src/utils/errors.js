/** @file Errors utility helpers */
class AppError extends Error {
    constructor(message, status = 500, code = 'INTERNAL_ERROR') {
        super(message);
        this.status = status;
        this.code = code;
    }
}

class ValidationError extends AppError {
    constructor(message) {
        super(message, 400, 'INVALID_PARAM');
    }
}

class NotFoundError extends AppError {
    constructor(message = 'Not Found') {
        super(message, 404, 'NOT_FOUND');
    }
}

class UnauthorizedError extends AppError {
    constructor(message = 'Unauthorized') {
        super(message, 401, 'UNAUTHORIZED');
    }
}

class ForbiddenError extends AppError {
    constructor(message = 'Forbidden') {
        super(message, 403, 'FORBIDDEN');
    }
}

module.exports = {
    AppError,
    ValidationError,
    NotFoundError,
    UnauthorizedError,
    ForbiddenError
};
