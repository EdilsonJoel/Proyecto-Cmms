package com.mantenimiento.cmms.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hash de contrasenas con SHA-256 y sal aleatoria por usuario.
 * El formato guardado es {@code sal:hash} para poder verificar sin volver a generar la sal.
 */
public final class Seguridad {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Seguridad() {
    }

    /** Genera {@code sal:hash} a partir de la contrasena en texto plano. */
    public static String hashear(String contrasena) {
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        String hexSal = HexFormat.of().formatHex(sal);
        return hexSal + ":" + digest(hexSal + contrasena);
    }

    /** Verifica una contrasena en texto plano contra el valor guardado. */
    public static boolean coincide(String contrasena, String guardado) {
        if (guardado == null || !guardado.contains(":") || contrasena == null) {
            return false;
        }
        String sal = guardado.substring(0, guardado.indexOf(':'));
        return MessageDigest.isEqual(
                digest(sal + contrasena).getBytes(StandardCharsets.UTF_8),
                guardado.substring(guardado.indexOf(':') + 1).getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(String texto) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
