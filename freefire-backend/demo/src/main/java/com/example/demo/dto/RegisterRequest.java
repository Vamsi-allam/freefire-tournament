package com.example.demo.dto;

import com.example.demo.entity.Role;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    private String email;
    private String name;
    private String phonenumber;
    private Role role;
}
