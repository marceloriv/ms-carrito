package com.ticketti.ms_carrito.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Date;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventoInfoDto {

    private Integer id;
    private String nombre;
    private Date fecha;
    private RecintoInfoDto recinto;
}
