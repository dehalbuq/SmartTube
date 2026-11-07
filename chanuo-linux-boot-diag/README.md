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
