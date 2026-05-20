
package com.ticketti.ms_carrito.dto;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.DetalleCarrito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.ticketti.ms_carrito.model.ItemPedido;
import com.ticketti.ms_carrito.model.Pedido;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenCarritoDto {
    private Long carritoId;
    private String estadoCarrito;
    private String estadoPago;
    private List<ItemCarritoDto> items;
    private BigDecimal subtotal;
    private BigDecimal montoDonacion;
    private BigDecimal total;
    private Integer totalEntradas;
    private LocalDateTime fechaExpiracionReserva;
    private Boolean puedeRenovarReserva;
    private Long causaSocialId;
    private Long reservaId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemCarritoDto {
        private Long detalleId;
        private Long eventoId;
        private String tipoEntrada;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
        private Long reservaId;
    }

    /**
     * Convierte un {@link CarritoDeCompras} (modelo vigente en el servicio) a DTO.
     * Compatible con campos de {@link DetalleCarrito}.
     */
    public static ResumenCarritoDto fromCarrito(CarritoDeCompras carrito) {
        List<DetalleCarrito> detalles = Optional.ofNullable(carrito.getDetalles()).orElse(List.of());
        List<ItemCarritoDto> itemsDto = detalles.stream()
            .map(detalle -> ItemCarritoDto.builder()
                .detalleId(detalle.getIdDetalleCarrito())
                .eventoId(detalle.getEventoId())
                .tipoEntrada(detalle.getTipoEntradaNombre())
                .cantidad(detalle.getCantidad())
                .precioUnitario(detalle.getPrecioUnitario())
                .subtotal(detalle.getSubtotal())
                .reservaId(detalle.getIdReserva())
                .build())
            .collect(java.util.stream.Collectors.toList());

        return ResumenCarritoDto.builder()
            .carritoId(carrito.getIdCarrito())
            .estadoCarrito(carrito.getEstadoCarrito() != null ? carrito.getEstadoCarrito().name() : null)
            .estadoPago(carrito.getEstadoPago() != null ? carrito.getEstadoPago().name() : null)
            .items(itemsDto)
            .subtotal(carrito.getSubtotal())
            .montoDonacion(carrito.getMontoDonacion())
            .total(carrito.getTotal())
            .totalEntradas(carrito.getTotalEntradas())
            .fechaExpiracionReserva(carrito.getFechaExpiracionReserva())
            .puedeRenovarReserva(carrito.puedeRenovarReserva())
            .causaSocialId(carrito.getCausaSocialId())
            .reservaId(carrito.getReservaId())
            .build();
    }
}