package br.com.dehalbuq.chanuobootdiag;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Non-root exporter for readable, non-sensitive device tree properties.
 *
 * Reads only whitelisted /proc and /sys paths. No shell, su or exploitation.
 * Safely skips credentials/device identifiers and inaccessible device tree nodes.
 * Does NOT copy raw FDT blob, boot images or arbitrary partitions.
 */
public final class DeviceTreeCollector {
    public interface Progress { void onProgress(String text); }
    private static final int MAX_ENTRIES = 3000;
    private static final long MAX_FILE = 512L * 1024L;
    private static final long MAX_TOTAL = 32L * 1024L * 1024L;
    private static int entries;
    private static long total;

    private static final String[] SENSITIVE = {
        "serial", "imei", "me id", "meid", "mac-address", "mac_address",
        "macaddr", "ethaddr", "wifi_mac", "bluetooth_addr", "password", "passwd",
        "private", "key", "token", "secret", "signature", "pubkey", "cert",
        "uuid", "seed", "efuse", "otp", "calibration", "calib-data"
    };

    private DeviceTreeCollector() {}

    private static boolean sensitive(String fullPath) {
        String lower = fullPath.toLowerCase(Locale.US);
        for (String part : SENSITIVE) {
            if (lower.contains(part)) return true;
        }
        return false;
    }

    private static void addText(ZipOutputStream zip, String name, String content)
            throws IOException {
        byte[] data = content.getBytes("UTF-8");
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] readLimited(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (n == 0) continue;
                if (out.size() + (long)n > MAX_FILE) {
                    throw new IOException("Arquivo acima de 512 KiB");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void addBytes(ZipOutputStream zip, String name, byte[] data)
            throws IOException {
        if (total + data.length > MAX_TOTAL) throw new IOException("Limite total 32 MiB");
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
        entries++;
        total += data.length;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(hash.length*2);
            for (byte b: hash) sb.append(String.format(Locale.US, "%02x", b & 255));
            return sb.toString();
        } catch (Exception ignored) { return "unavailable"; }
    }

    private static void traverse(File root, File current, ZipOutputStream zip,
                                 StringBuilder index, StringBuilder errors,
                                 Progress progress, int depth) {
        if (depth > 20 || entries >= MAX_ENTRIES || total >= MAX_TOTAL) return;
        try {
            String rootCanonical = root.getCanonicalPath();
            String fileCanonical = current.getCanonicalPath();
            if (!fileCanonical.equals(rootCanonical) &&
                    !fileCanonical.startsWith(rootCanonical + "/")) {
                errors.append("SYMLINK EXTERNO: ").append(current.getPath()).append('\n');
                return;
            }
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children == null) {
                    errors.append("DIRETORIO BLOQUEADO: ").append(current.getPath()).append('\n');
                    return;
                }
                Arrays.sort(children, (a,b)-> a.getName().compareTo(b.getName()));
                for (File child:children) {
                    if (entries >= MAX_ENTRIES || total >= MAX_TOTAL) break;
                    traverse(root, child, zip, index, errors, progress, depth + 1);
                }
                return;
            }
            String rel = current.getPath().substring(root.getPath().length());
            while (rel.startsWith("/")) rel=rel.substring(1);
            if (sensitive(rel)) {
                errors.append("OMITIDO PARA PRIVACIDADE: ").append(rel).append('\n');
                return;
            }
            // Bootargs can carry serialno and cannot safely be shared unmodified.
            if (rel.equals("chosen/bootargs") || rel.startsWith("chosen/atag,")) {
                errors.append("OMITIDO PARA PRIVACIDADE: ").append(rel)
                        .append(" (bootargs constam, com serial removido, no TXT)\n");
                return;
            }
            byte[] bytes=readLimited(current);
            addBytes(zip, "devicetree/" + rel, bytes);
            index.append("devicetree/").append(rel).append(" | ")
                    .append(bytes.length).append(" bytes | SHA256 ")
                    .append(sha256(bytes)).append('\n');
            if (entries % 80 == 0)
                progress.onProgress("Device Tree: " + entries + " propriedades exportadas...");
        } catch (Exception e) {
            errors.append("INACESSIVEL: ").append(current.getPath())
                    .append(" - ").append(e.getClass().getSimpleName()).append('\n');
        }
    }

    private static void extra(ZipOutputStream zip, String path, String outName,
                              StringBuilder errors) {
        if (sensitive(path)) return;
        try {
            byte[] data=readLimited(new File(path));
            addBytes(zip, "hardware/" + outName, data);
        } catch (Exception e) {
            errors.append("INACESSIVEL: ").append(path)
                .append(" - ").append(e.getClass().getSimpleName()).append('\n');
        }
    }

    public static String export(Context ctx, Uri dest, String report, Progress progress) {
        entries=0; total=0;
        StringBuilder index = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        try (OutputStream file=ctx.getContentResolver().openOutputStream(dest)) {
            if (file==null) throw new IOException("Destino indisponivel");
            try (ZipOutputStream zip=new ZipOutputStream(file)) {
                addText(zip, "LEIA-ME.txt",
                    "Chanuo A1: coleta Linux SEM ROOT, somente leitura.\n" +
                    "Os arquivos sao propriedades binarias de Device Tree.\n" +
                    "Sem acesso a particoes ou preloader; nao e firmware nem imagem bootavel.\n" +
                    "Algumas propriedades com identificadores sao omitidas.\n" +
                    "Nao publique o ZIP integral: pode conter detalhes do hardware.\n");
                addText(zip, "diagnostico_anterior.txt", report==null?"":report);
                progress.onProgress("Lendo Device Tree sem root...");
                File root = new File("/proc/device-tree");
                if (!root.exists()) root=new File("/sys/firmware/devicetree/base");
                if (root.exists() && root.isDirectory())
                    traverse(root, root, zip, index, errors, progress, 0);
                else errors.append("Device Tree indisponivel em /proc e /sys.\n");

                progress.onProgress("Lendo interfaces CPU/GPU/USB e memoria...");
                extra(zip, "/proc/cpuinfo", "cpuinfo.txt", errors);
                extra(zip, "/proc/meminfo", "meminfo.txt", errors);
                extra(zip, "/proc/partitions", "partitions.txt", errors);
                extra(zip, "/proc/version", "kernel_version.txt", errors);
                extra(zip, "/sys/block/mmcblk0/size", "mmc_size.txt", errors);
                extra(zip, "/sys/block/mmcblk0/device/name", "mmc_name.txt", errors);
                extra(zip, "/sys/class/graphics/fb0/name", "framebuffer_name.txt", errors);
                extra(zip, "/sys/class/graphics/fb0/modes", "framebuffer_modes.txt", errors);
                extra(zip, "/sys/class/android_usb/android0/state", "usb_state.txt", errors);
                extra(zip, "/sys/class/android_usb/android0/functions", "usb_functions.txt", errors);
                for(int cpu=0;cpu<4;cpu++) {
                    String cp="/sys/devices/system/cpu/cpu"+cpu+"/cpufreq/";
                    extra(zip,cp+"cpuinfo_max_freq","cpu"+cpu+"_max_khz.txt",errors);
                    extra(zip,cp+"scaling_cur_freq","cpu"+cpu+"_current_khz.txt",errors);
                    extra(zip,cp+"scaling_available_frequencies","cpu"+cpu+"_available_khz.txt",errors);
                }
                addText(zip,"manifesto_device_tree.txt",
                    "Arquivos exportados: "+entries+"\nBytes exportados: "+total+"\n\n"+index);
                addText(zip,"acessos_bloqueados_ou_omitidos.txt",errors.toString());
                zip.finish();
            }
            return "ZIP Device Tree salvo ("+entries+" arquivos, "+
                    (total/1024)+" KiB). Envie o ZIP para investigar o Linux.";
        } catch (Throwable t) {
            return "Falha: "+t.getClass().getSimpleName()+" - "+t.getMessage()+
                   ". Ignore ZIP parcial, caso tenha sido criado.";
        }
    }
}
