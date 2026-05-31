package com.ticketti.ms_carrito.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CausaSocialInfoDto {

    private Long idCausa;
    private String nombre;
}
