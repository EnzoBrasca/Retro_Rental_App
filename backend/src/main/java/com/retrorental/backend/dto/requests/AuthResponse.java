package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Rol;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String nombre;
    private String apellido;
    private String email;
    private Rol rol;
}