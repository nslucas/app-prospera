package com.example.prospera.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterDTO(String name, String lastName, BigDecimal monthLimit, String email, String password) {

}
