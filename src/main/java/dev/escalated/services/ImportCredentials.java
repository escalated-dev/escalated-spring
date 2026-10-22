package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Seals an import source's credentials (AES-256-GCM) for where they rest: the admin's session,
 * from a tested connection until the import starts, and the import job's {@code credentials}
 * column until the run ends or is cancelled. The key comes from {@link GuestAccessKeys}, so it
 * follows {@code escalated.guest-access.secret}.
 */
@Component
public class ImportCredentials {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] CONTEXT = "escalated-import-credentials".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public ImportCredentials(GuestAccessKeys keys) {
        this.key = new SecretKeySpec(keys.derive("import-credentials"), "AES");
    }

    public String seal(Map<String, String> credentials) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(CONTEXT);
            byte[] sealed = cipher.doFinal(JSON.writeValueAsBytes(credentials));
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the import credentials.", e);
        }
    }

    /** The credentials, or null when absent, tampered with or sealed under another key. */
    public Map<String, String> open(String sealed) {
        if (sealed == null || sealed.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(sealed);
            if (raw.length < 13) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, raw, 0, 12));
            cipher.updateAAD(CONTEXT);
            byte[] plain = cipher.doFinal(raw, 12, raw.length - 12);
            return JSON.readValue(plain, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (GeneralSecurityException | IllegalArgumentException | java.io.IOException e) {
            return null;
        }
    }
}
