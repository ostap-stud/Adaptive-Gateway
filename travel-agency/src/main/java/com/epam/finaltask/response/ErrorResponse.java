package com.epam.finaltask.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private String errorHeader;
    private String errorBody;
    private String errorCode;
}
