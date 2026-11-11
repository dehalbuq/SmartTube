# Chanuo A1 – Diagnóstico Linux/Boot/USB (ARMv7)

APK **independente** do SmartTube e do diagnóstico anterior. Instala no projetor e faz levantamento do kernel, CPU, RAM, API real, Device Tree, partições, eMMC, capacidades USB host/gadget, Android props, framebuffer e existência de nós de bootloader.

**Segurança**: não requer root, depuração USB, Internet nem permissões de armazenamento. Não executa `su`, `dd`, `fastboot`, `flash`, `reboot`, ou escrita em partições; coleta por APIs públicas e arquivos de sistema **somente leitura**. Guarda o relatório automaticamente em sua pasta de arquivos e também permite **Salvar TXT (escolher pasta)** e **Compartilhar texto**. O relatório não inclui serial do dispositivo, número CID/serial da eMMC, nem MAC; bootargs conhecidos contendo identificadores são ocultados.

**Limitação**: Android sem root pode bloquear `/proc/cmdline`, Device Tree, nós de eMMC ou partições. O relatório marca informações inacessíveis, sem supor que não existam. Capacidade de `/data` é tamanho da partição, **não** capacidade física total da eMMC.

### Gerar APK
Branch `chanuo-a1-linux-boot-diag` → Actions → **Compilar Chanuo A1 Linux Boot Diagnostico** → artifact `ChanuoA1-LinuxBoot-Diagnostico-APK` (ZIP contém `app-debug.apk`).
SDK 35, min API 19, compilação Java Android ARM universal sem bibliotecas nativas.

### Utilização
1. Instalar APK; aparece também no launcher Android TV.
2. Abrir e coletar (automático); se necessário selecionar **Coletar novamente**.
3. Selecionar **Salvar TXT (escolher pasta)** e escolher Downloads. Se firmware não possuir seletor, usar **Compartilhar texto**.
4. Enviar `ChanuoA1_Boot_USB_Hardware.txt` nesta conversa.

A ausência de gadget USB ou das informações de Device Tree não confirma inviabilidade do projeto; são etapas para selecionar a técnica de boot sem arriscar a firmware.


## Root Collector v2 (branch `chanuo-a1-root-collector`)

Aplicativo com outro **applicationId**, instalável em paralelo ao diagnóstico antigo. Não contém exploit, usa `su -c` **somente se root temporário já estiver ativo** e autorizado pelo usuário. Executa comandos de leitura para consultar o bootloader, Device Tree, configurações de kernel e interfaces USB. Exporta um único ZIP via seletor de arquivos do Android com logs e, se disponíveis, `boot.img`, `recovery.img`, `lk.img`, `logo.img`, `dtb/dtbo.img`, além de `/sys/firmware/fdt` e `/proc/config.gz`. Valida tamanho a partir de `/proc/partitions` e calcula SHA256. Limite: 64 MiB por partição, 5 MiB por arquivo pequeno.

**Não inclui** preloader, userdata, nvram, nvdata, protect, persist, metadata, RPMB ou conteúdo pessoal. **Não constitui backup completo da firmware**. Não remonta, atualiza, formata, faz flash nem desbloqueia. O comando `dd` tem exclusivamente `if=` (leitura do bloco; saída para stream ZIP) e NUNCA `of=` apontando dispositivo. Testar inicialmente root temporário com MTK Easy SU oficial. Se `su` não estiver disponível ou autorizado, exportador não lê blocos. A firmware pode bloquear a leitura independentemente do UID.

Workflow desta branch compila o APK `ChanuoA1-Root-Collector-APK`.
