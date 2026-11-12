# Chanuo A1 – Fastboot Windows: script de consulta (sem gravacao)

Corrige o erro "fastboot.exe nao e reconhecido" ocorrido porque o Stage 1 procurava em scripts/platform-tools ou PATH e o usuario tinha Platform-Tools numa pasta adjacente.

- Usa -FastbootPath opcional e procura em caminhos comuns, inclusive ../platform-tools relativo ao ZIP.
- Falha com mensagem clara caso fastboot nao esteja instalado; nunca conclui que o dispositivo esta ausente nesse caso.
- So consulta dispositivos e, quando um unico dispositivo de modo Fastboot responde, variaveis de leitura do bootloader.
- Necessita do aparelho em Fastboot Mode; MediaTek PreLoader USB VCOM nao e Fastboot.
- Nao executa comandos de boot/flash/unlock.
- Nao repita conexao host USB-A macho-macho comum sem determinar o isolamento de alimentacao.

Exemplo Windows:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\01_VERIFICAR_FASTBOOT_WINDOWS.ps1 -FastbootPath 'D:\Área de Trabalho\platform-tools\fastboot.exe'
```

O kernel experimental produzido pela CI e da familia MT6737, mas NAO foi validado no Chanuo. Nenhuma tentativa de gravacao deve ser realizada.
