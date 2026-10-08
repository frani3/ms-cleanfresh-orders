package com.cleanfresh.ms_cleanfresh_orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "ordenes")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_orden", unique = true)
    private String numeroOrden;

    private String cliente;
    private String servicio;
    private String estado;
    private LocalDate fecha;
    private Double total;
    private String sucursal;

    // Nombre legible del cliente (Spec 032); null en las ordenes anteriores.
    @Column(name = "cliente_nombre")
    private String clienteNombre;

    protected OrderEntity() {
    }

    public OrderEntity(String numeroOrden, String cliente, String servicio, String estado,
                       LocalDate fecha, Double total, String sucursal) {
        this.numeroOrden = numeroOrden;
        this.cliente = cliente;
        this.servicio = servicio;
        this.estado = estado;
        this.fecha = fecha;
        this.total = total;
        this.sucursal = sucursal;
    }

    public Long getId() {
        return id;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public void setNumeroOrden(String numeroOrden) {
        this.numeroOrden = numeroOrden;
    }

    public String getCliente() {
        return cliente;
    }

    public String getServicio() {
        return servicio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Double getTotal() {
        return total;
    }

    public String getSucursal() {
        return sucursal;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }
}
