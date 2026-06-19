package com.betsim.domain;

/** Enumeraciones del dominio agrupadas para mantener el paquete compacto. */
public final class Enums {
    private Enums() {}

    public enum Rol { USER, ADMIN }

    public enum Fase { GRUPOS, OCTAVOS, CUARTOS, SEMIS, TERCER_PUESTO, FINAL }

    public enum EstadoPartido { PROGRAMADO, EN_JUEGO, FINALIZADO, LIQUIDADO }

    public enum TipoMercado { UNO_X_DOS, GOLEADOR }

    public enum EstadoMercado { ABIERTO, CERRADO, LIQUIDADO }

    public enum TipoApuesta { SIMPLE, COMBINADA }

    public enum EstadoApuesta { PENDIENTE, GANADA, PERDIDA, ANULADA }

    public enum ResultadoSeleccion { PENDIENTE, ACERTADA, FALLADA, ANULADA }

    /** Resultado real 1X2 a 90 minutos. */
    public enum Resultado1x2 { LOCAL, EMPATE, VISITANTE }

    public enum TipoTransaccion { BONO_INICIAL, APUESTA, PREMIO, BONO_DIARIO, RESET }
}
