package com.codepath.academy.dto;

import java.io.Serializable;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageDTO implements Serializable {

    private static final long serialVersionUID = 2L;
    private String to;
    private String fullName;
    private String type;
    private String otpCode;
    private Long orderId;
}
