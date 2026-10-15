package com.goal_tracker.auth.exceptions;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;

public class IncorrectCurrentPasswordException extends BadRequestException{
    public IncorrectCurrentPasswordException() {
        super(ErrorCode.INVALID_CURRENT_PASSWORD.getDefaultMessage(), ErrorCode.INVALID_CURRENT_PASSWORD);
    }
    
}
