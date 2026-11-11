package br.com.dehalbuq.chanuobootdiag;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Read-only root-assisted collector. No exploit bundled. Requires su granted
 * by a root manager. Never executes mount/remount, flash, fastboot, or writes to
 * block devices, sysfs, /system, /vendor, or /data.
 *
 * Deliberately excludes userdata, metadata, persist, nvram, nvdata, protect,
 * security partitions, RPMB, full eMMC and serial numbers.
 */
public final class RootCollector {
    public interface Progress { void onProgress(String message); }
    private static final String BLOCK_BASE =
            "/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/";
    private static final String[] PARTITION_NAMES =
            {"boot", "recovery", "lk", "logo", "dtbo", "dtb"};
    private static final long MAX_PARTITION = 64L * 1024L * 1024L;
    private static final long MAX_SMALL_FILE = 5L * 1024L * 1024L;
    private static final Pattern SAFE_BLOCK =
            Pattern.compile("^/dev/block/mmcblk[0-9]+p[0-9]+$");

    private RootCollector() {}

    private static String redact(String input) {
        return input.replaceAll("(?i)(androidboot\\.serialno|serialno|wifi_mac|ethaddr|macaddr|mac_address)=\\S+",
                "$1=[OCULTO]");
    }

    private static String rootText(String cmd, int maxChars) throws Exception {
        java.lang.Process p = new ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true).start();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        try (InputStream in = p.getInputStream()) {
            int n;
            while ((n = in.read(chunk)) >= 0) {
                if (n == 0) continue;
                if (data.size() + n > maxChars) {
                    p.destroy();
                    throw new IOException("Saida de comando maior que limite: " + cmd);
                }
                data.write(chunk, 0, n);
            }
        }
        int code = p.waitFor();
        String value = data.toString("UTF-8").trim();
        if (code != 0) return "[Exit " + code + "] " + value;
        return value;
    }

    private static void addText(ZipOutputStream zip, String name, String txt)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(txt.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private static String asRootTextReport() {
        StringBuilder b = new StringBuilder();
        b.append("CHANUO A1 - ROOT READ-ONLY COLLECTOR v2\n");
        b.append("Nenhum bloco escrito. Nao e um backup completo da firmware.\n");
        b.append("Dados sensiveis e userdata nao coletados.\n\n");
        String[][] commands = {
            {"UID e privilegios", "id"},
            {"Kernel", "uname -a"},
            {"SELinux", "getenforce"},
            {"Kernel cmdline", "cat /proc/cmdline"},
            {"Versao kernel", "cat /proc/version"},
            {"Mapa de blocos", "cat /proc/partitions"},
            {"Memoria", "cat /proc/meminfo"},
            {"Symlinks de particoes", "ls -l " + BLOCK_BASE},
            {"Dispositivos USB", "ls -l /sys/class/udc"},
            {"USB Android", "cat /sys/class/android_usb/android0/state"},
            {"USB funcoes", "cat /sys/class/android_usb/android0/functions"},
            {"CPU frequencias", "ls -l /sys/devices/system/cpu/cpu0/cpufreq"},
            {"GPU dispositivos", "ls -l /sys/class/devfreq"},
            {"Device Tree top-level", "ls -l /proc/device-tree"},
            {"Device Tree modelo", "cat /proc/device-tree/model"},
            {"Device Tree compatible", "cat /proc/device-tree/compatible"},
            {"Device Tree bootargs", "cat /proc/device-tree/chosen/bootargs"}
        };
        for (String[] item : commands) {
            b.append("\n#### ").append(item[0]).append("\n");
            try {
                b.append(redact(rootText(item[1], 100000))).append('\n');
            } catch (Throwable t) {
                b.append("[INACESSIVEL] ").append(t.toString()).append('\n');
            }
        }
        return b.toString();
    }

    private static Map<String, Long> readPartitionSizes() {
        Map<String, Long> sizes = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/partitions"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 4 && p[3].matches("mmcblk[0-9]+p[0-9]+")) {
                    long kib = Long.parseLong(p[2]);
                    if (kib > 0 && kib < Long.MAX_VALUE / 1024L) {
                        sizes.put(p[3], kib * 1024L);
                    }
                }
            }
        } catch (Throwable ignored) { }
        return sizes;
    }

    private static String digestHex(byte[] digest) {
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        return hex.toString();
    }

    private static long streamRootFile(ZipOutputStream zip, String entryName,
                                       String path, String command,
                                       long maxAllowed, long expected,
                                       StringBuilder manifest) throws Exception {
        java.lang.Process p = new ProcessBuilder("su", "-c", command).start();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        byte[] buffer = new byte[32768];
        long written = 0;
        boolean exceeds = false;
        try (InputStream in = p.getInputStream()) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                if (written + n > maxAllowed) {
                    exceeds = true;
                    p.destroy();
                    break;
                }
                zip.write(buffer, 0, n);
                digest.update(buffer, 0, n);
                written += n;
            }
        } finally {
            zip.closeEntry();
        }
        int rc;
        try { rc = p.waitFor(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        boolean valid = !exceeds && rc == 0 && (expected < 0 || written == expected);
        manifest.append(entryName).append(" | source=").append(path)
                .append(" | bytes=").append(written)
                .append(" | esperado=").append(expected)
                .append(" | status=").append(valid ? "OK" : "INCOMPLETO/ERRO")
                .append(" | sha256=").append(digestHex(digest.digest()))
                .append('\n');
        return written;
    }

    private static void addSmallFile(ZipOutputStream zip, String path,
                                     String entry, StringBuilder manifest) {
        try {
            // Commands are fixed strings controlled by this app, not user input.
            streamRootFile(zip, entry, path, "cat " + path + " 2>/dev/null",
                    MAX_SMALL_FILE, -1L, manifest);
        } catch (Throwable t) {
            manifest.append(entry).append(" | nao disponivel: ")
                    .append(t.getClass().getSimpleName()).append('\n');
        }
    }

    public static String export(Context ctx, Uri destination,
                                String originalReport, Progress progress) {
        progress.onProgress("Verificando se o root temporario foi autorizado...");
        String id;
        try { id = rootText("id", 15000); }
        catch (Throwable t) {
            return "Root nao disponivel: " + t.getClass().getSimpleName()
                    + ". Abra MTK Easy SU e ative o root antes da coleta.";
        }
        if (!(id.contains("uid=0(") || id.startsWith("uid=0 ") || id.equals("0"))) {
            return "Root nao concedido. Resultado do comando id: " + id
                    + ". Autorize o Chanuo Root Collector no Magisk.";
        }
        progress.onProgress("Root confirmado! Exportando diagnostico...");
        StringBuilder manifest = new StringBuilder();
        manifest.append("CHANUO A1 - ROOT COLLECTOR v2 (somente leitura)\n");
        manifest.append("ARQUIVOS NAO SAO FIRMWARE PRONTA PARA GRAVACAO.\n");
        manifest.append("Exclui userdata, NV, RPMB, particoes seguras e preloader.\n");
        manifest.append("Nao publique este ZIP em repositorios publicos.\n");
        Map<String, Long> sizes = readPartitionSizes();
        int recovered = 0;
        try (OutputStream file = ctx.getContentResolver().openOutputStream(destination)) {
            if (file == null) throw new IOException("Nao foi possivel abrir arquivo ZIP");
            try (ZipOutputStream zip = new ZipOutputStream(file)) {
                zip.setLevel(1);
                addText(zip, "LEIA-ME.txt",
                        "Arquivos extraidos exclusivamente para analise do hardware.\n" +
                        "NAO GRAVE os binarios no projetor nem use como firmware de atualizacao.\n" +
                        "Particoes de dados pessoais e preloader nao foram copiadas.\n" +
                        "Este ZIP NAO substitui backup completo antes de desbloquear bootloader.\n");
                addText(zip, "diagnostico_sem_root.txt",
                        originalReport == null ? "Indisponivel\n" : originalReport);
                addText(zip, "diagnostico_root.txt", asRootTextReport());
                addSmallFile(zip, "/sys/firmware/fdt", "hardware/device-tree.dtb", manifest);
                addSmallFile(zip, "/proc/config.gz", "hardware/kernel-config.gz", manifest);

                for (String partition : PARTITION_NAMES) {
                    progress.onProgress("Procurando particao " + partition + "...");
                    String alias = BLOCK_BASE + partition;
                    String actual;
                    try {
                        actual = rootText("readlink -f " + alias, 1000).trim();
                    } catch (Throwable t) {
                        manifest.append(partition).append(" | erro ao resolver link\n");
                        continue;
                    }
                    if (!SAFE_BLOCK.matcher(actual).matches()) {
                        manifest.append(partition).append(" | inexistente ou nao seguro: ")
                                .append(actual).append('\n');
                        continue;
                    }
                    String leaf = actual.substring(actual.lastIndexOf('/') + 1);
                    Long bytes = sizes.get(leaf);
                    if (bytes == null || bytes == 0 || bytes > MAX_PARTITION) {
                        manifest.append(partition).append(" | tamanho ausente/invalido ou >64 MiB: ")
                                .append(bytes).append('\n');
                        continue;
                    }
                    long blocks = (bytes + 4095) / 4096;
                    progress.onProgress("Lendo " + partition + " (" +
                            (bytes / (1024 * 1024)) + " MiB), somente leitura...");
                    try {
                        long n = streamRootFile(zip, "particoes/" + partition + ".img",
                                actual, "dd if=" + actual + " bs=4096 count=" +
                                        blocks + " 2>/dev/null",
                                MAX_PARTITION, bytes, manifest);
                        if (n == bytes) recovered++;
                    } catch (Throwable t) {
                        manifest.append(partition).append(" | falha ao ler: ")
                                .append(t.toString()).append('\n');
                    }
                }
                addText(zip, "manifesto-integridade.txt", manifest.toString());
                zip.finish();
            }
        } catch (Throwable t) {
            return "Falha na exportacao: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage() +
                    ". Se aparecer ZIP parcial, nao considere um backup valido.";
        }
        return "ZIP exportado. Particoes lidas com tamanho valido: " +
                recovered + ". Envie o ZIP para analisarmos o boot Linux.";
    }
}
