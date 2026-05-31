package com.ticketti.ms_carrito.messaging;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompraConfirmadaEvent {

    private Long idCarrito;
    private Long pagoId;
    private Long usuarioId;
    private Long causaSocialId;
    private BigDecimal total;
    private BigDecimal montoDonacion;
    private Long eventoId;
    private String correoUsuario;
    private String nombreUsuario;
    private String nombreEvento;
    private String fechaEvento;
    private String lugarEvento;
    private String codigoQr;
    private String nombreCausa;
}
