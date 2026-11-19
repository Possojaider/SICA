package com.sica.infrastructure;

import com.sica.domain.SicaException;
import com.sica.domain.port.out.SavedLoginStore;
import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class MacKeychainLoginStore implements SavedLoginStore {
    private static final int NOT_FOUND = -25300;
    private static final int DUPLICATE = -25299;
    private final String service;

    public MacKeychainLoginStore(String service) {
        this.service = Objects.requireNonNull(service);
    }

    public static SavedLoginStore forConnection(String url) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return SavedLoginStore.unavailable();
        }
        try {
            String scope = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(StandardCharsets.UTF_8)));
            return new MacKeychainLoginStore("com.sica.desktop.login." + scope);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 no está disponible.", failure);
        }
    }

    @Override
    public boolean available() { return true; }

    @Override
    public Optional<Login> load() {
        try (Dictionary query = query()) {
            query.constant("kSecReturnData", Core.LIBRARY.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0));
            query.constant("kSecMatchLimit", Security.constant("kSecMatchLimitOne"));
            PointerByReference result = new PointerByReference();
            int status = Security.API.SecItemCopyMatching(query.pointer, result);
            if (status == NOT_FOUND) return Optional.empty();
            check(status);
            Pointer data = result.getValue();
            if (data == null) throw new SicaException("El llavero no devolvió los datos guardados.");
            try {
                long length = Core.API.CFDataGetLength(data).longValue();
                if (length <= 0 || length > 8192) throw new SicaException("Los datos guardados no se pudieron recuperar.");
                byte[] payload = Core.API.CFDataGetBytePtr(data).getByteArray(0, (int) length);
                try (var input = new DataInputStream(new ByteArrayInputStream(payload))) {
                    Login login = new Login(input.readUTF(), input.readUTF());
                    if (input.available() != 0 || login.email().isBlank() || login.password().isEmpty()) {
                        throw new IOException("Registro de credenciales inválido");
                    }
                    return Optional.of(login);
                } catch (IOException failure) {
                    throw new SicaException("Los datos guardados no se pudieron recuperar. Puedes olvidarlos y guardarlos otra vez.");
                } finally {
                    Arrays.fill(payload, (byte) 0);
                }
            } finally {
                Core.API.CFRelease(data);
            }
        } catch (LinkageError failure) {
            throw nativeUnavailable(failure);
        }
    }

    @Override
    public void save(Login login) {
        if (login.email() == null || login.email().isBlank() || login.password() == null || login.password().isEmpty()) {
            throw new SicaException("Completa el usuario y la contraseña antes de guardarlos.");
        }
        byte[] payload = encode(login);
        try (Dictionary query = query(); Dictionary attributes = new Dictionary()) {
            attributes.data("kSecValueData", payload);
            int status = Security.API.SecItemUpdate(query.pointer, attributes.pointer);
            if (status == NOT_FOUND) {
                query.data("kSecValueData", payload);
                query.string("kSecAttrLabel", "SICA · Cuenta recordada");
                status = Security.API.SecItemAdd(query.pointer, null);
                if (status == DUPLICATE) {
                    try (Dictionary retry = query()) {
                        status = Security.API.SecItemUpdate(retry.pointer, attributes.pointer);
                    }
                }
            }
            check(status);
        } catch (LinkageError failure) {
            throw nativeUnavailable(failure);
        } finally {
            Arrays.fill(payload, (byte) 0);
        }
    }

    @Override
    public void clear() {
        try (Dictionary query = query()) {
            int status = Security.API.SecItemDelete(query.pointer);
            if (status != NOT_FOUND) check(status);
        } catch (LinkageError failure) {
            throw nativeUnavailable(failure);
        }
    }

    private static SicaException nativeUnavailable(LinkageError failure) {
        org.slf4j.LoggerFactory.getLogger(MacKeychainLoginStore.class).warn("Integración nativa del llavero no disponible.", failure);
        return new SicaException("La integración con el llavero no está disponible en esta instalación.");
    }

    private Dictionary query() {
        Dictionary query = new Dictionary();
        query.constant("kSecClass", Security.constant("kSecClassGenericPassword"));
        query.string("kSecAttrService", service);
        query.string("kSecAttrAccount", "remembered-login");
        return query;
    }

    private static byte[] encode(Login login) {
        try (var buffer = new ByteArrayOutputStream(); var data = new DataOutputStream(buffer)) {
            data.writeUTF(login.email());
            data.writeUTF(login.password());
            return buffer.toByteArray();
        } catch (IOException failure) {
            throw new SicaException("No se pudieron preparar los datos de acceso.");
        }
    }

    private static void check(int status) {
        if (status == 0) return;
        org.slf4j.LoggerFactory.getLogger(MacKeychainLoginStore.class).warn("El llavero de macOS devolvió OSStatus={}", status);
        throw new SicaException("No se pudo acceder al llavero de macOS. Revisa su desbloqueo o autorización.");
    }

    private interface SecurityApi extends Library {
        int SecItemCopyMatching(Pointer query, PointerByReference result);
        int SecItemAdd(Pointer attributes, PointerByReference result);
        int SecItemUpdate(Pointer query, Pointer attributes);
        int SecItemDelete(Pointer query);
    }

    private interface CoreApi extends Library {
        Pointer CFDictionaryCreateMutable(Pointer allocator, NativeLong capacity, Pointer keys, Pointer values);
        void CFDictionarySetValue(Pointer dictionary, Pointer key, Pointer value);
        Pointer CFStringCreateWithCString(Pointer allocator, byte[] value, int encoding);
        Pointer CFDataCreate(Pointer allocator, byte[] bytes, NativeLong length);
        NativeLong CFDataGetLength(Pointer data);
        Pointer CFDataGetBytePtr(Pointer data);
        void CFRelease(Pointer value);
    }

    private static final class Security {
        private static final NativeLibrary LIBRARY = NativeLibrary.getInstance(
                "/System/Library/Frameworks/Security.framework/Security");
        private static final SecurityApi API = Native.load(LIBRARY.getName(), SecurityApi.class);

        private static Pointer constant(String name) { return LIBRARY.getGlobalVariableAddress(name).getPointer(0); }
    }

    private static final class Core {
        private static final NativeLibrary LIBRARY = NativeLibrary.getInstance(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation");
        private static final CoreApi API = Native.load(LIBRARY.getName(), CoreApi.class);
    }

    private static final class Dictionary implements AutoCloseable {
        private final Pointer pointer = Core.API.CFDictionaryCreateMutable(null, new NativeLong(0),
                Core.LIBRARY.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
                Core.LIBRARY.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"));

        private void constant(String key, Pointer value) {
            Core.API.CFDictionarySetValue(pointer, Security.constant(key), value);
        }

        private void string(String key, String value) {
            byte[] utf8 = (value + "\0").getBytes(StandardCharsets.UTF_8);
            Pointer string = Core.API.CFStringCreateWithCString(null, utf8, 0x08000100);
            try { constant(key, string); } finally { Core.API.CFRelease(string); }
        }

        private void data(String key, byte[] value) {
            Pointer data = Core.API.CFDataCreate(null, value, new NativeLong(value.length));
            try { constant(key, data); } finally { Core.API.CFRelease(data); }
        }

        @Override public void close() { Core.API.CFRelease(pointer); }
    }
}
