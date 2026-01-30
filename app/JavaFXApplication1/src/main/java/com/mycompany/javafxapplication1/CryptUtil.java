/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
/**
 *
 * @author ntu-user
 */
public final class CryptoUtil {
    //256bit AES key stored locally so it persists across runs
    private static final Path KEY_FILE = Configuration.STORAGE_LOCAL_DIR.resolve(".aes_key_b64");

    private static final String ALGO = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12; // standard for GCM

    private static volatile SecretKey cachedKey;

    private CryptoUtil() {}

    private static SecretKey getOrCreateKey() throws IOException {
        if (cachedKey != null) return cachedKey;

        synchronized (CryptoUtil.class) {
            if (cachedKey != null) return cachedKey;

            if (Files.exists(KEY_FILE)) {
                String b64 = Files.readString(KEY_FILE).trim();
                byte[] raw = Base64.getDecoder().decode(b64);
                cachedKey = new SecretKeySpec(raw, ALGO);
                return cachedKey;
            }
            //create new key file
            byte[] raw = new byte[32]; //256-bit
            new SecureRandom().nextBytes(raw);
            String b64 = Base64.getEncoder().encodeToString(raw);
            Files.writeString(KEY_FILE, b64);
            cachedKey = new SecretKeySpec(raw, ALGO);

            AppLogger.warn("AES KEY CREATED at " + KEY_FILE.toAbsolutePath()
                    + " (keep this file or old encrypted files can't be decrypted)");
            return cachedKey;
        }
    }

    public static byte[] encrypt(byte[] plaintext) throws Exception {
        SecretKey key = getOrCreateKey();
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext);

        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }

    public static byte[] decrypt(byte[] ivAndCiphertext) throws Exception {
        if (ivAndCiphertext == null || ivAndCiphertext.length < IV_BYTES + 16) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        SecretKey key = getOrCreateKey();
        byte[] iv = new byte[IV_BYTES];
        byte[] ct = new byte[ivAndCiphertext.length - IV_BYTES];

        System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_BYTES);
        System.arraycopy(ivAndCiphertext, IV_BYTES, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ct);
    }
}
