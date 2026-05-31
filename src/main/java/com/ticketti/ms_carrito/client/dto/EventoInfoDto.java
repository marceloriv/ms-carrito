package com.ticketti.ms_carrito.client.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventoInfoDto {

    private Integer id;
    private String nombre;
    private Date fecha;
    private RecintoInfoDto recinto;
}
