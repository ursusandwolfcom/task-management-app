package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.attachment.api.MegaException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One SDK instance per operation; binary length framing, private pipes, no shell or shared cache. */
public class NativeMegaClient implements MegaClient {
    private final boolean enabled;
    private final Path executable;
    private final String appKey;
    private final int timeoutSeconds;
    private final Semaphore slots = new Semaphore(2);

    public NativeMegaClient(boolean enabled, String executable, String appKey, int timeoutSeconds) {
        this.enabled = enabled;
        this.executable = Path.of(executable).toAbsolutePath();
        this.appKey = appKey == null ? "" : appKey.strip();
        if (timeoutSeconds < 1 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("mega.timeout-seconds must be within 1..600");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean configured() {
        return enabled && !appKey.isEmpty() && Files.isRegularFile(executable) && Files.isExecutable(executable);
    }

    @Override
    public Session login(String email, String password, String mfaCode, String folderName) {
        var result = invoke("login", email, password, mfaCode, folderName);
        if (result.size() != 4) {
            throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
        }
        return new Session(result.get(1), result.get(2), result.get(3));
    }

    @Override
    public String upload(Session session, Path source, String key) {
        var result = invoke("upload", session.session(), session.accountId(), session.folderHandle(),
                source.toAbsolutePath().toString(), key);
        if (result.size() != 2) {
            throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
        }
        return result.get(1);
    }

    @Override
    public void download(Session session, String handle, Path destination) {
        invoke("download", session.session(), session.accountId(), handle, destination.toAbsolutePath().toString());
    }

    @Override
    public void remove(Session session, String handle) {
        invoke("remove", session.session(), session.accountId(), handle);
    }

    private List<String> invoke(String operation, String... arguments) {
        if (!configured()) {
            throw new MegaException(MegaException.Reason.MEGA_NOT_CONFIGURED);
        }
        if (!slots.tryAcquire()) {
            throw new MegaException(MegaException.Reason.MEGA_BUSY);
        }
        try {
            return invokeProcess(operation, arguments);
        } finally {
            slots.release();
        }
    }

    private List<String> invokeProcess(String operation, String[] arguments) {
        Process process;
        try {
            var builder = new ProcessBuilder(executable.toString()).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            // The SDK must not inherit application database/JWT/session-encryption secrets.
            for (String name : List.of("PATH", "SystemRoot", "WINDIR", "TEMP", "TMP", "LANG", "LC_ALL",
                    "SSL_CERT_FILE", "SSL_CERT_DIR", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY",
                    "http_proxy", "https_proxy", "all_proxy", "no_proxy")) {
                String value = System.getenv(name);
                if (value != null) {
                    builder.environment().put(name, value);
                }
            }
            process = builder.start();
        } catch (IOException exception) {
            throw new MegaException(MegaException.Reason.MEGA_NOT_CONFIGURED);
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var response = executor.submit(() -> exchange(process, operation, arguments));
            try {
                return response.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                process.destroyForcibly();
                response.cancel(true);
                throw new MegaException(MegaException.Reason.MEGA_TIMEOUT);
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                response.cancel(true);
                Thread.currentThread().interrupt();
                throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof MegaException mega) {
                    throw mega;
                }
                throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
            } finally {
                process.destroyForcibly();
            }
        }
    }

    private List<String> exchange(Process process, String operation, String[] arguments) throws IOException {
        try (var output = new DataOutputStream(process.getOutputStream())) {
            output.writeInt(arguments.length + 2);
            write(output, operation);
            write(output, appKey);
            for (String argument : arguments) {
                write(output, argument);
            }
        }
        List<String> fields = new ArrayList<>();
        try (var input = new DataInputStream(process.getInputStream())) {
            int count = input.readInt();
            if (count < 1 || count > 4) {
                throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
            }
            for (int index = 0; index < count; index++) {
                int length = input.readInt();
                if (length < 0 || length > 16384) {
                    throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
                }
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) {
                    throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
                }
                fields.add(new String(bytes, StandardCharsets.UTF_8));
            }
            if (input.read() != -1) {
                throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
            }
        }
        try {
            if (process.waitFor() != 0) {
                throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
        }
        if (!"OK".equals(fields.getFirst())) {
            try {
                throw new MegaException(MegaException.Reason.valueOf(fields.getFirst()));
            } catch (IllegalArgumentException exception) {
                throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
            }
        }
        return List.copyOf(fields);
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        if (value == null || value.indexOf('\0') >= 0) {
            throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16384) {
            throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
