package com.cleanfresh.ms_cleanfresh_orders.event;

/**
 * Quién hizo una acción y desde dónde, para la auditoría (Spec 044). El BFF los manda en las cabeceras {@code X-Actor}
 * (el {@code username} del token) y {@code X-Origen} (la IP del cliente); si no llegan, queda "desconocido".
 *
 * @param usuario el {@code username} de Cognito de quien hizo la acción
 * @param origen  desde dónde (IP del cliente), o null
 */
public record Actor(String usuario, String origen) {

    public static final Actor DESCONOCIDO = new Actor("desconocido", null);

    /** Normaliza los valores que llegan por cabecera: en blanco es "sin dato". */
    public static Actor de(String usuario, String origen) {
        String u = usuario == null || usuario.isBlank() ? null : usuario.trim();
        String o = origen == null || origen.isBlank() ? null : origen.trim();
        return u == null && o == null ? DESCONOCIDO : new Actor(u == null ? DESCONOCIDO.usuario() : u, o);
    }
}
