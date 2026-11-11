package br.com.dehalbuq.chanuobootdiag;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Standalone hardware / boot diagnostic with optional root-assisted read-only exports.
 * The optional RootCollector invokes su only after user approval and reads whitelisted partitions.
 * Only writes a TXT report to the app's files directory or a user-chosen URI.
 */
public final class MainActivity extends Activity {
    private static final int PICK_SAVE_TXT = 101;
    private static final int PICK_ROOT_ZIP = 102;
    private static final int MAX_TEXT = 20000;
    private TextView status;
    private TextView preview;
    private String report = "";
    private String autoPath = "";

    private static final String[] PROPERTY_KEYS = {
        "ro.hardware", "ro.board.platform", "ro.mediatek.platform",
        "ro.product.board", "ro.product.device", "ro.product.model",
        "ro.product.cpu.abi", "ro.product.cpu.abilist", "ro.build.type",
        "ro.build.tags", "ro.build.version.release", "ro.build.version.sdk",
        "ro.build.version.security_patch", "ro.build.fingerprint",
        "ro.bootmode", "ro.boot.mode", "ro.boot.hardware", "ro.boot.bootreason",
        "ro.boot.verifiedbootstate", "ro.boot.flash.locked",
        "ro.boot.vbmeta.device_state", "ro.boot.slot_suffix",
        "ro.boot.avb_version", "ro.secure", "ro.debuggable",
        "ro.adb.secure", "persist.sys.usb.config", "sys.usb.config",
        "sys.usb.state", "ro.boot.usbcontroller", "ro.crypto.state",
        "ro.crypto.type", "ro.opengles.version", "ro.kernel.qemu"
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroller = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setPadding(26, 20, 26, 32);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xff1c2331);
        scroller.addView(content);

        TextView heading = new TextView(this);
        heading.setText("Chanuo A1 — Linux/Boot Diagnostico");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(23f);
        content.addView(heading);

        TextView intro = new TextView(this);
        intro.setText("Sem ADB, sem root e sem internet. Lê apenas dados públicos do kernel, boot, eMMC, USB, Device Tree e firmware. Se uma informação estiver bloqueada, o relatório explicará.");
        intro.setTextSize(16f);
        intro.setTextColor(0xffe1e7ee);
        intro.setPadding(0, 14, 0, 18);
        content.addView(intro);

        addButton(content, "1. Coletar novamente", v -> collect());
        addButton(content, "2. Salvar TXT (escolher pasta)", v -> saveWithPicker());
        addButton(content, "3. Compartilhar texto do relatorio", v -> shareReport());
        addButton(content, "4. Exportar ZIP de boot (requer ROOT)", v -> saveRootPackage());

        status = new TextView(this);
        status.setTextSize(16f);
        status.setTextColor(0xffb9e6c7);
        status.setPadding(0, 20, 0, 12);
        content.addView(status);

        preview = new TextView(this);
        preview.setTextSize(13f);
        preview.setTextColor(0xffd7d7d7);
        preview.setTextIsSelectable(true);
        content.addView(preview);
        setContentView(scroller);
        collect();
    }

    private void addButton(LinearLayout root, String title, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(16f);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setOnClickListener(listener);
        root.addView(b);
    }

    private void collect() {
        status.setText("Coletando informacoes (somente leitura)...");
        preview.setText("");
        new Thread(() -> {
            String result;
            try { result = makeReport(); }
            catch (Throwable t) { result = "Falha inesperada: " + t.toString(); }
            String saved = saveAutomatically(result);
            final String finalResult = result;
            final String finalSaved = saved;
            runOnUiThread(() -> {
                report = finalResult;
                autoPath = finalSaved;
                status.setText("Coleta concluida: " + report.length()
                    + " caracteres.\nCopia automatica: " + autoPath
                    + "\nUse 'Salvar TXT (escolher pasta)' para salvar em Downloads.");
                preview.setText(report.substring(0, Math.min(report.length(), 7500))
                    + (report.length() > 7500 ? "\n[Previa resumida; exporte TXT completo]" : ""));
            });
        }, "chanuo-bootdiag").start();
    }

    private static void entry(StringBuilder out, String key, Object value) {
        out.append(key).append(": ").append(String.valueOf(value)).append('\n');
    }
    private static void section(StringBuilder out, String name) {
        out.append("\n========== ").append(name).append(" ==========\n");
    }
    private static String markPrivate(String input) {
        return input.replaceAll("(?i)(androidboot\\.serialno|serialno|wifi_mac|ethaddr|macaddr|mac_address)=\\S+", "$1=[OCULTO]");
    }

    private static String readText(String path, int limit) {
        File file = new File(path);
        if (!file.exists()) return "[NAO EXISTE]";
        if (file.isDirectory()) return "[DIRETORIO]";
        try (FileInputStream stream = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = stream.read(buf)) > 0) {
                int toWrite = Math.min(n, limit - output.size());
                if (toWrite > 0) output.write(buf, 0, toWrite);
                if (output.size() >= limit) break;
            }
            return markPrivate(output.toString("UTF-8").replace('\u0000', ' ').trim());
        } catch (Throwable t) {
            return "[BLOQUEADO/ERRO: " + t.getClass().getSimpleName() + ": "
                + t.getMessage() + "]";
        }
    }

    private static void file(StringBuilder out, String path, int max) {
        out.append("\n$ ").append(path).append("\n")
            .append(readText(path, max)).append('\n');
    }
    private static String childNames(String path, int limit) {
        File dir = new File(path);
        if (!dir.exists()) return "[NAO EXISTE]";
        if (!dir.isDirectory()) return "[NAO E DIRETORIO]";
        try {
            String[] names = dir.list();
            if (names == null) return "[SEM PERMISSAO PARA LISTAR]";
            Arrays.sort(names);
            if (names.length > limit) {
                return Arrays.toString(Arrays.copyOf(names, limit)) +
                        " ... (" + names.length + " entradas)";
            }
            return Arrays.toString(names);
        } catch (Throwable e) {
            return "[BLOQUEADO: " + e.getClass().getSimpleName() + "]";
        }
    }
    private static void dir(StringBuilder out, String path, int limit) {
        entry(out, path, childNames(path, limit));
    }

    private static String formatBytes(long bytes) {
        return String.format(Locale.US, "%d bytes (%.2f GiB)", bytes,
                bytes / 1073741824.0);
    }

    private void storage(StringBuilder out, String label, File path) {
        try {
            StatFs st = new StatFs(path.getAbsolutePath());
            long total = Build.VERSION.SDK_INT >= 18 ? st.getTotalBytes()
                    : ((long) st.getBlockCount()) * st.getBlockSize();
            long available = Build.VERSION.SDK_INT >= 18 ? st.getAvailableBytes()
                    : ((long) st.getAvailableBlocks()) * st.getBlockSize();
            entry(out, label + " - caminho", path.getAbsolutePath());
            entry(out, label + " - tamanho da particao", formatBytes(total));
            entry(out, label + " - livre", formatBytes(available));
        } catch (Throwable t) {
            entry(out, label, "[ERRO: " + t + "]");
        }
    }

    private static void blockDevices(StringBuilder out) {
        section(out, "ARMAZENAMENTO FISICO / SYSFS BLOCK");
        String root = "/sys/block";
        dir(out, root, 80);
        File[] blocks = new File(root).listFiles();
        if (blocks == null) {
            out.append("Impossivel listar /sys/block; propriedades podem exigir root.\n");
            return;
        }
        Arrays.sort(blocks, (a,b) -> a.getName().compareTo(b.getName()));
        for (File d : blocks) {
            String name = d.getName();
            if (!(name.startsWith("mmcblk") || name.startsWith("sda")
                || name.startsWith("sdb") || name.startsWith("ufs")
                || name.startsWith("nvme"))) continue;
            out.append("\n[Dispositivo] ").append(name).append('\n');
            String sectorsText = readText(d.getPath() + "/size", 70);
            entry(out, "setores de 512 bytes (informados)", sectorsText);
            try {
                long sectors = Long.parseLong(sectorsText.trim());
                if (sectors >= 0 && sectors <= Long.MAX_VALUE / 512) {
                    entry(out, "capacidade bruta anunciada", formatBytes(sectors * 512));
                }
            } catch (Exception ignored) {
                entry(out, "capacidade bruta", "indisponivel");
            }
            entry(out, "removivel", readText(d.getPath()+"/removable", 80));
            entry(out, "tipo/modelo", readText(d.getPath()+"/device/name", 150));
            entry(out, "modelo SCSI", readText(d.getPath()+"/device/model", 150));
            entry(out, "bloco logico", readText(d.getPath()+"/queue/logical_block_size", 80));
            entry(out, "particoes", childNames(d.getPath(), 50));
            // Intencionalmente nao le CID/serial da eMMC.
        }
    }

    private static void properties(StringBuilder out) {
        section(out, "PROPRIEDADES ANDROID (LISTA SEGURA)");
        Set<String> accepted = new HashSet<>(Arrays.asList(PROPERTY_KEYS));
        java.lang.Process p = null;
        try {
            p = new ProcessBuilder("getprop").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                String line;
                int lines = 0;
                while ((line = br.readLine()) != null && lines++ < 3000) {
                    int sep = line.indexOf("]: [");
                    if (!line.startsWith("[") || sep < 1) continue;
                    String key = line.substring(1, sep);
                    if (accepted.contains(key)) {
                        entry(out, key, markPrivate(line.substring(sep + 4)
                                .replaceAll("\\]$", "")));
                    }
                }
            }
            p.waitFor();
        } catch (Throwable t) {
            entry(out, "getprop", "[ERRO: " + t + "]");
        } finally {
            if (p != null) p.destroy();
        }
    }

    private String makeReport() {
        StringBuilder b = new StringBuilder(36000);
        SimpleDateFormat clock = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        clock.setTimeZone(TimeZone.getTimeZone("UTC"));
        b.append("CHANUO A1 - DIAGNOSTICO AVANCADO BOOT/LINUX USB v1\n");
        b.append("Somente leitura; nenhuma propriedade/particao foi alterada.\n");
        b.append("NOTA: Android sem root pode bloquear informacoes importantes.\n");
        b.append("NOTA: firmware pode falsificar versao/identidade; isso nao valida hardware fisico.\n");
        entry(b, "Data UTC", clock.format(new Date()));
        entry(b, "UID do app", Process.myUid());

        section(b, "ANDROID / SOC / MEMORIA");
        entry(b, "Build.MANUFACTURER", Build.MANUFACTURER);
        entry(b, "Build.BRAND", Build.BRAND);
        entry(b, "Build.MODEL", Build.MODEL);
        entry(b, "Build.PRODUCT", Build.PRODUCT);
        entry(b, "Build.DEVICE", Build.DEVICE);
        entry(b, "Build.HARDWARE", Build.HARDWARE);
        entry(b, "Build.BOARD", Build.BOARD);
        entry(b, "Build.VERSION.RELEASE (pode ser falso)", Build.VERSION.RELEASE);
        entry(b, "Build.VERSION.SDK_INT (API efetiva)", Build.VERSION.SDK_INT);
        entry(b, "Build.ID", Build.ID);
        entry(b, "Build.TAGS", Build.TAGS);
        entry(b, "ABI", Build.VERSION.SDK_INT >= 21
                ? Arrays.toString(Build.SUPPORTED_ABIS) : Build.CPU_ABI);
        entry(b, "Nucleos anunciados", Runtime.getRuntime().availableProcessors());
        if (Build.VERSION.SDK_INT >= 23)
            entry(b, "Security patch", Build.VERSION.SECURITY_PATCH);
        try {
            ActivityManager mgr = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            mgr.getMemoryInfo(info);
            entry(b, "RAM total Android", formatBytes(info.totalMem));
            entry(b, "RAM disponivel Android", formatBytes(info.availMem));
            entry(b, "Low RAM", mgr.isLowRamDevice());
            entry(b, "GLES anunciado", mgr.getDeviceConfigurationInfo().getGlEsVersion());
        } catch (Throwable t) { entry(b, "Memoria Android", t.toString()); }

        section(b, "KERNEL / PROC");
        file(b, "/proc/version", 4000);
        file(b, "/proc/cpuinfo", 10000);
        file(b, "/proc/meminfo", 5000);
        file(b, "/proc/cmdline", 5000);
        file(b, "/proc/partitions", 6000);
        file(b, "/proc/mounts", 12000);
        file(b, "/proc/filesystems", 4000);
        file(b, "/proc/device-tree/model", 1000);
        file(b, "/proc/device-tree/compatible", 2000);
        file(b, "/proc/device-tree/chosen/bootargs", 4000);
        file(b, "/sys/firmware/devicetree/base/model", 1000);
        file(b, "/sys/firmware/devicetree/base/compatible", 2000);
        file(b, "/sys/firmware/devicetree/base/chosen/bootargs", 4000);
        dir(b, "/proc/device-tree", 75);
        dir(b, "/sys/firmware/devicetree/base", 75);
        dir(b, "/sys/devices/soc0", 30);
        file(b, "/sys/devices/soc0/machine", 500);
        file(b, "/sys/devices/soc0/family", 500);
        file(b, "/sys/devices/soc0/soc_id", 500);
        file(b, "/sys/devices/soc0/revision", 500);

        properties(b);

        section(b, "PARTICOES E CAPACIDADE DE ARMAZENAMENTO");
        storage(b, "Particao /data (NAO e o total da eMMC)", new File("/data"));
        storage(b, "Particao /system", new File("/system"));
        storage(b, "Armazenamento compartilhado", Environment.getExternalStorageDirectory());
        file(b, "/proc/mtd", 5000);
        file(b, "/proc/emmc", 5000);
        blockDevices(b);
        dir(b, "/dev/block", 70);
        dir(b, "/dev/block/platform", 50);
        dir(b, "/dev/block/by-name", 70);

        section(b, "USB HOST / GADGET / ADB");
        PackageManager pm = getPackageManager();
        entry(b, "USB Host declarado", pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST));
        entry(b, "USB Accessory declarado", pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY));
        dir(b, "/sys/class/udc", 35);
        dir(b, "/sys/class/android_usb", 35);
        dir(b, "/sys/class/usb_role", 35);
        dir(b, "/sys/class/typec", 35);
        dir(b, "/sys/bus/usb/devices", 60);
        file(b, "/sys/class/android_usb/android0/enable", 300);
        file(b, "/sys/class/android_usb/android0/state", 300);
        file(b, "/sys/class/android_usb/android0/functions", 300);
        try {
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            Map<String,UsbDevice> connected = manager.getDeviceList();
            entry(b, "Dispositivos USB conectados (nao serial)", connected.size());
            for (UsbDevice dev : connected.values()) {
                entry(b, "USB ID VID:PID", String.format(Locale.US, "%04x:%04x",
                        dev.getVendorId(), dev.getProductId())
                        + " class=" + dev.getDeviceClass());
            }
        } catch (Throwable t) { entry(b, "Dispositivos USB", t.toString()); }

        section(b, "DISPLAY / FRAMEBUFFER / DRIVER GRAFICO");
        try {
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getRealSize(size);
            entry(b, "Framebuffer Android", size.x + "x" + size.y);
            entry(b, "Refresh", display.getRefreshRate());
        } catch (Throwable t) { entry(b, "Display", t.toString()); }
        dir(b, "/sys/class/graphics", 30);
        file(b, "/sys/class/graphics/fb0/name", 300);
        file(b, "/sys/class/graphics/fb0/modes", 1000);
        file(b, "/sys/class/graphics/fb0/virtual_size", 500);
        dir(b, "/sys/class/drm", 50);
        dir(b, "/dev/dri", 30);

        section(b, "POSSIBILIDADE DE ROOT / RECUPERACAO (SEM EXECUTAR SU)");
        entry(b, "Caminho /system/xbin/su presente", new File("/system/xbin/su").exists());
        entry(b, "Caminho /system/bin/su presente", new File("/system/bin/su").exists());
        entry(b, "Caminho /sbin/su presente", new File("/sbin/su").exists());
        entry(b, "Caminho /vendor/bin", new File("/vendor/bin").exists());
        dir(b, "/dev/block/bootdevice/by-name", 65);
        dir(b, "/sys/firmware", 35);
        b.append("\nIMPORTANTE: ausência de acesso a arquivo nao prova ausência do hardware.\n");
        b.append("Nenhuma tentativa de root/alteracao de bootloader foi feita.\nFIM.\n");
        return b.toString();
    }

    private String saveAutomatically(String data) {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Criacao de pasta falhou");
            File out = new File(dir, "ChanuoA1_Boot_USB_Hardware.txt");
            try (FileOutputStream stream = new FileOutputStream(out)) {
                stream.write(data.getBytes("UTF-8"));
                stream.flush();
            }
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return "[Falha ao salvar automaticamente: " + t + "]";
        }
    }

    private void saveWithPicker() {
        if (report.length() == 0) {
            Toast.makeText(this, "Primeiro execute a coleta", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "ChanuoA1_Boot_USB_Hardware.txt");
            startActivityForResult(intent, PICK_SAVE_TXT);
        } catch (Throwable t) {
            Toast.makeText(this, "Seletor indisponivel. Use Compartilhar texto. "
                    + autoPath, Toast.LENGTH_LONG).show();
        }
    }

    private void saveRootPackage() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, "ChanuoA1_RootBoot_Collector.zip");
            startActivityForResult(i, PICK_ROOT_ZIP);
        } catch (Throwable t) {
            Toast.makeText(this, "Seletor de arquivo indisponivel: " + t,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareReport() {
        if (report.length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "Chanuo A1 - Boot Linux USB");
            i.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(i, "Compartilhar diagnostico"));
        } catch (Throwable t) {
            Toast.makeText(this, "Compartilhamento indisponivel: " + t,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_SAVE_TXT && result == RESULT_OK &&
                data != null && data.getData() != null) {
            try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new java.io.IOException("Falha na abertura do arquivo");
                output.write(report.getBytes("UTF-8"));
                Toast.makeText(this, "TXT salvo. Envie o arquivo na conversa.",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(this, "Falha ao salvar TXT: " + t,
                        Toast.LENGTH_LONG).show();
            }
        } else if (request == PICK_ROOT_ZIP && result == RESULT_OK &&
                data != null && data.getData() != null) {
            final android.net.Uri uri = data.getData();
            status.setText("Solicitando root: autorize este app no Magisk se aparecer uma janela.");
            new Thread(() -> {
                String outcome;
                try {
                    outcome = RootCollector.export(this, uri, report,
                            progress -> runOnUiThread(() -> status.setText(progress)));
                } catch (Throwable t) {
                    outcome = "Erro ao criar ZIP: " + t.getClass().getSimpleName()
                            + " - " + t.getMessage();
                }
                final String message = outcome;
                runOnUiThread(() -> {
                    status.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }, "chanuo-root-export").start();
        }
    }
}
