package com.epam.finaltask.exception.handler;

import com.epam.finaltask.exception.ExistingUserException;
import com.epam.finaltask.exception.InvalidCredentialsException;
import com.epam.finaltask.exception.RepeatAuthenticationException;
import com.epam.finaltask.request.SignInRequest;
import com.epam.finaltask.request.SignUpRequest;
import com.epam.finaltask.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ModelAndView handleInvalidCredentials(InvalidCredentialsException e) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("error", e.getMessage());
        modelAndView.addObject("signInRequest", new SignInRequest());
        modelAndView.setViewName("auth/sign-in");
        return modelAndView;
    }

    @ExceptionHandler(ExistingUserException.class)
    public ModelAndView handleExistingUser(ExistingUserException e) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("error", e.getMessage());
        modelAndView.addObject("signUpRequest", new SignUpRequest());
        modelAndView.setViewName("auth/sign-up");
        return modelAndView;
    }

    @ExceptionHandler(RepeatAuthenticationException.class)
    public ModelAndView handleRepeatAuthentication(RepeatAuthenticationException e) {
       return errorPage(
               new ErrorResponse(
                       "You've been signed in already.",
                       "Sign out to re-authenticate.",
                       String.valueOf(HttpStatus.NOT_FOUND.value())
               )
       );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNoHandlerFound(NoResourceFoundException e) {
        return errorPage(
                new ErrorResponse(
                        "Oops! You're lost.",
                        "The page you are looking for was not found.",
                        String.valueOf(HttpStatus.NOT_FOUND.value())
                )
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNoHandlerFound(HttpRequestMethodNotSupportedException e) {
        return errorPage(
                new ErrorResponse(
                        "Oops! You're lost.",
                        "This service is not implemented yet.",
                        String.valueOf(HttpStatus.NOT_FOUND.value())
                )
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleInternalError(Exception e) {
        return errorPage(
                new ErrorResponse(
                        "Sorry! Something went wrong.",
                        "Reload the page or try again later...",
                        String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value())
                )
        );
    }

    private ModelAndView errorPage(ErrorResponse errorResponse) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("errorResponse", errorResponse);
        modelAndView.setViewName("error");
        return modelAndView;
    }

}
