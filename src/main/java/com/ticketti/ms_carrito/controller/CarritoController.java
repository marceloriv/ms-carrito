package com.ticketti.ms_carrito.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketti.ms_carrito.dto.AgregarEntradaDto;
import com.ticketti.ms_carrito.dto.ApiRespuestaDto;
import com.ticketti.ms_carrito.dto.CheckoutDto;
import com.ticketti.ms_carrito.dto.DevolucionRequestDto;
import com.ticketti.ms_carrito.dto.DevolucionResponseDto;
import com.ticketti.ms_carrito.dto.ResumenCarritoDto;
import com.ticketti.ms_carrito.dto.WebhookPagoDto;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.service.CarritoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/Carrito")
@Tag(name = "Carrito", description = "API para gestion de carrito de compras y orquestacion de pagos")
public class CarritoController {

    private static final Logger log = LoggerFactory.getLogger(CarritoController.class);
    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @Operation(summary = "Crear nuevo carrito", description = "Inicia una nueva compra creando un carrito vacio")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Carrito creado exitosamente",
                content = @Content(schema = @Schema(implementation = CarritoDeCompras.class))),
        @ApiResponse(responseCode = "400", description = "Datos invalidos")
    })
    @PostMapping("/crear")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> crearCarrito(
            @Parameter(description = "ID del usuario (null para invitados)")
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @Parameter(description = "Rol del usuario (null para invitados)")
            @RequestHeader(value = "X-Rol-Usuario-Id", required = false) String rolUsuario) {
        log.info("POST /api/v1/Carrito/crear - Usuario: {}, Rol: {}", usuarioId, rolUsuario);
        CarritoDeCompras carrito = carritoService.crearCarrito(usuarioId, rolUsuario);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiRespuestaDto.exito("Carrito creado exitosamente", carrito));
    }

    @Operation(summary = "Obtener carrito por ID", description = "Obtiene un carrito especifico por su ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Carrito obtenido exitosamente"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @GetMapping("/obtener/{id}")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> obtenerCarrito(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("GET /api/v1/Carrito/obtener/{} - Usuario: {}", id, usuarioId);
        CarritoDeCompras carrito = carritoService.obtenerCarrito(id, usuarioId);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Carrito obtenido", carrito));
    }

    @Operation(summary = "Listar carritos del usuario", description = "Obtiene el historial de carritos del usuario")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de carritos obtenida exitosamente"),
        @ApiResponse(responseCode = "204", description = "No hay carritos")
    })
    @GetMapping("/listar")
    public ResponseEntity<ApiRespuestaDto<List<CarritoDeCompras>>> listarCarritos(
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("GET /api/v1/Carrito/listar - Usuario: {}", usuarioId);
        List<CarritoDeCompras> carritos = carritoService.listarCarritosPorUsuario(usuarioId);
        if (carritos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ApiRespuestaDto.exito("Carritos encontrados", carritos));
    }

    @Operation(summary = "Obtener resumen del carrito", description = "Obtiene el detalle completo del carrito")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Resumen obtenido exitosamente",
                content = @Content(schema = @Schema(implementation = ResumenCarritoDto.class))),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @GetMapping("/resumen/{id}")
    public ResponseEntity<ApiRespuestaDto<ResumenCarritoDto>> obtenerResumen(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("GET /api/v1/Carrito/resumen/{} - Usuario: {}", id, usuarioId);
        ResumenCarritoDto resumen = carritoService.obtenerResumen(id, usuarioId);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Resumen obtenido", resumen));
    }

    @Operation(summary = "Agregar entrada al carrito", description = "Agrega una entrada al carrito existente")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Entrada agregada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Limite de entradas excedido o datos invalidos"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @PostMapping("/{id}/entradas")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> agregarEntrada(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId,
            @Valid @RequestBody AgregarEntradaDto dto) {
        log.info("POST /api/v1/Carrito/{}/entradas - Usuario: {}", id, usuarioId);
        CarritoDeCompras carrito = carritoService.agregarEntrada(id, usuarioId, dto);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Entrada agregada", carrito));
    }

    @Operation(summary = "Eliminar entrada del carrito", description = "Elimina una entrada especifica del carrito")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Entrada eliminada exitosamente"),
        @ApiResponse(responseCode = "404", description = "Carrito o entrada no encontrada")
    })
    @DeleteMapping("/{id}/entradas/{detalleId}")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> eliminarEntrada(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del detalle", required = true) @PathVariable Long detalleId,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("DELETE /api/v1/Carrito/{}/entradas/{} - Usuario: {}", id, detalleId, usuarioId);
        CarritoDeCompras carrito = carritoService.eliminarEntrada(id, detalleId, usuarioId);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Entrada eliminada", carrito));
    }

    @Operation(summary = "Actualizar cantidad de entradas", description = "Actualiza el carrito respetando maximo 4 entradas")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Carrito actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Limite excedido o datos invalidos"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @PutMapping("/actualizar/{id}")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> actualizarCarrito(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId,
            @Valid @RequestBody AgregarEntradaDto dto) {
        log.info("PUT /api/v1/Carrito/actualizar/{} - Usuario: {}", id, usuarioId);
        CarritoDeCompras carrito = carritoService.agregarEntrada(id, usuarioId, dto);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Carrito actualizado", carrito));
    }

    @Operation(summary = "Checkout - Iniciar pago", description = "Reserva stock, genera idempotencia y prepara para pago")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Checkout iniciado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Carrito vacio o sin stock"),
        @ApiResponse(responseCode = "409", description = "Error de idempotencia"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @PostMapping("/checkout/{id}")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> iniciarCheckout(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId,
            @Valid @RequestBody CheckoutDto dto) {
        log.info("POST /api/v1/Carrito/checkout/{} - Usuario: {}", id, usuarioId);
        CarritoDeCompras carrito = carritoService.iniciarCheckout(id, usuarioId, dto);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Checkout iniciado. Reserva de 5 min activa.", carrito));
    }

    @Operation(summary = "Renovar reserva de stock", description = "Renueva la reserva por 2 minutos adicionales (una sola vez)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reserva renovada exitosamente"),
        @ApiResponse(responseCode = "410", description = "No se puede renovar la reserva")
    })
    @PostMapping("/renovar/{id}")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> renovarReserva(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("POST /api/v1/Carrito/renovar/{} - Usuario: {}", id, usuarioId);
        CarritoDeCompras carrito = carritoService.renovarReserva(id, usuarioId);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Reserva renovada por 2 minutos adicionales", carrito));
    }

    @Operation(summary = "Webhook de confirmacion de pago", description = "Recibe confirmacion asincrona de la pasarela de pagos")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pago procesado exitosamente"),
        @ApiResponse(responseCode = "401", description = "Webhook invalido"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @PostMapping("/webhooks/pago")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> procesarWebhookPago(
            @Valid @RequestBody WebhookPagoDto dto) {
        log.info("POST /api/v1/Carrito/webhooks/pago - Carrito: {}", dto.getPedidoId());
        CarritoDeCompras carrito = carritoService.procesarWebhookPago(dto.getPedidoId(), dto);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Pago procesado", carrito));
    }

    @Operation(summary = "Procesar pago manual (simulado)", description = "Procesa un pago manual para desarrollo/pruebas sin pasarela de pagos")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pago procesado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Estado invalido del carrito"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @PostMapping("/pago-manual/{id}")
    public ResponseEntity<ApiRespuestaDto<CarritoDeCompras>> procesarPagoManual(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("POST /api/v1/Carrito/pago-manual/{} - Usuario: {}", id, usuarioId);
        CarritoDeCompras carrito = carritoService.procesarPagoManual(id, usuarioId);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Pago manual procesado exitosamente", carrito));
    }

    @Operation(summary = "Solicitar devolucion", description = "Procesa reembolso del 85% (10% donacion no reembolsable)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Devolucion procesada exitosamente"),
        @ApiResponse(responseCode = "403", description = "Devolucion no permitida"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @PostMapping("/devoluciones/{id}")
    public ResponseEntity<ApiRespuestaDto<DevolucionResponseDto>> procesarDevolucion(
            @Parameter(description = "ID del carrito", required = true) @PathVariable Long id,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId,
            @Valid @RequestBody DevolucionRequestDto dto) {
        log.info("POST /api/v1/Carrito/devoluciones/{} - Usuario: {}", id, usuarioId);
        DevolucionResponseDto respuesta = carritoService.procesarDevolucion(id, usuarioId, dto);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Devolucion procesada", respuesta));
    }

    @Operation(summary = "Vaciar carrito", description = "Elimina el carrito completo y libera reservas")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Carrito vaciado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Carrito no encontrado")
    })
    @DeleteMapping("/vaciar")
    public ResponseEntity<ApiRespuestaDto<Map<String, String>>> vaciarCarrito(
            @Parameter(description = "ID del carrito", required = true)
            @RequestHeader("X-Carrito-Id") Long carritoId,
            @Parameter(description = "ID del usuario", required = true)
            @RequestHeader("X-Usuario-Id") Long usuarioId) {
        log.info("DELETE /api/v1/Carrito/vaciar - Carrito: {}, Usuario: {}", carritoId, usuarioId);
        carritoService.vaciarCarrito(carritoId, usuarioId);
        return ResponseEntity.ok(ApiRespuestaDto.exito("Carrito vaciado exitosamente",
                Map.of("carritoId", carritoId.toString(), "estado", "CANCELADO")));
    }
}
