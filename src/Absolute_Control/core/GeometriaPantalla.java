package Absolute_Control.core;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;

/** Coordenadas de una sola pantalla, en el sistema de coordenadas de AWT/Robot. */
public final class GeometriaPantalla {
    public static final int TOLERANCIA_BORDE = 2;
    public static final int MARGEN_ENTRADA = 8;
    private final int ancho, alto;

    public GeometriaPantalla(int ancho, int alto) {
        if (ancho < 1 || alto < 1) throw new IllegalArgumentException("Dimensiones de pantalla invalidas");
        this.ancho = ancho; this.alto = alto;
    }

    public static GeometriaPantalla actual() {
        Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
        return new GeometriaPantalla(pantalla.width, pantalla.height);
    }

    public int ancho() { return ancho; }
    public int alto() { return alto; }
    public int izquierda() { return 0; }
    public int derecha() { return ancho - 1; }
    public Point centro() { return new Point(ancho / 2, alto / 2); }

    public boolean enBordeSalida(int x, boolean secundariaALaDerecha) {
        return secundariaALaDerecha ? x >= derecha() - TOLERANCIA_BORDE : x <= izquierda() + TOLERANCIA_BORDE;
    }

    public boolean enBordeRegreso(int x, boolean secundariaALaDerecha) {
        return enBordeSalida(x, !secundariaALaDerecha);
    }

    public double alturaRelativa(int y) {
        return alto == 1 ? 0 : Math.max(0, Math.min(alto - 1, y)) / (double) (alto - 1);
    }

    public static double validarAltura(double altura) {
        if (!Double.isFinite(altura) || altura < 0 || altura > 1)
            throw new IllegalArgumentException("Altura relativa fuera de [0,1]");
        return altura;
    }

    public Point entradaSecundaria(boolean secundariaALaDerecha, double altura) {
        int margen = Math.min(MARGEN_ENTRADA, (ancho - 1) / 2);
        int x = secundariaALaDerecha ? izquierda() + margen : derecha() - margen;
        return new Point(x, (int) Math.round(validarAltura(altura) * (alto - 1)));
    }

    public Point regresoPrincipal(boolean secundariaALaDerecha, double altura) {
        return entradaSecundaria(!secundariaALaDerecha, altura);
    }

    public Point desplazar(Point posicion, int dx, int dy) {
        return new Point((int) Math.max(0, Math.min(derecha(), (long) posicion.x + dx)),
                (int) Math.max(0, Math.min(alto - 1, (long) posicion.y + dy)));
    }
}
