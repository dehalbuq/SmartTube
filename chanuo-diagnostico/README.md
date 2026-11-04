# Chanuo A1 Diagnostico

App independente do SmartTube. Coleta dados divulgados pelo sistema Android: SoC, ABI, RAM, modos de display, GPU/GLES, decodificadores e encoders MediaCodec com limites declarados. **Nao valida decodificacao real, nem resolucao optica nativa do projetor.**

Minimo Android 4.4 (API 19). Sem permissoes de Internet, localizacao ou armazenamento. Use os botoes **Salvar relatorio TXT** ou **Compartilhar relatorio**. A disponibilidade do seletor de documentos depende do firmware.

## Gerar APK

Vá a Actions no GitHub, abra **Compilar Chanuo A1 Diagnostico**, e baixe o artifact `ChanuoA1-Diagnostico-APK`. O arquivo ZIP do artifact contem `app-debug.apk`. Se o Actions de forks estiver desativado, habilite-o nas configuracoes do repositorio.

## Nota

Este prototipo não modifica a firmware nem as cores de outros aplicativos. Os ajustes de gamma, contraste e saturacao serao implementados no SmartTube e validados fisicamente com o relatorio deste app.
