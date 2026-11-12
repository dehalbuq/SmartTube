# Chanuo A1 Stage 1 — kernel experimental de referência (ARMv7)
O objetivo da Fase 1 é **compilar** uma base de kernel MediaTek MT6737 e gerar um artefato analisável. **NÃO é boot comprovado pelo pendrive.**

- Hardware alvo: Chanuo A1, PCB T8G-MB-V2.0; MT6737V; Android API 27; kernel original 3.18.79; bootloader informa bloqueado.
- Fonte inicial de referência: [Nokia 1 — MT6737M](https://github.com/nokia-dev/android_kernel_nokia_mt6737m); **não** usa DTS do projetor.
- Build: GitHub Actions `.github/workflows/chanuo-a1-stage1-kernel.yml` chama `ci/build_kernel.sh`, compila apenas `zImage` ARMv7 e exporta logs, SHA e configuração. Sem código para gravar a eMMC.
- O kernel de referência não deve ser carregado na placa antes de checarmos a compatibilidade DTB, fontes de energia, relógios, relógios watchdog, console UART e endereços de carregamento. Pode travar ou reinicializar.
- O Device Tree extraído do projetor fica **fora** deste repositório para evitar publicar dados específicos da unidade.
- Não conectar portas USB-A de host diretamente por cabo macho-macho sem interface elétrica segura. A porta USB-C é entrada de 15 V do projetor.
- A detecção de MediaTek PreLoader VCOM no Windows é um avanço de comunicação, **não** suporte boot USB mass storage.
- Não usar `fastboot flash`, `fastboot oem unlock`, `fastboot flashing unlock`, nem SP Flash Tool Download ou MTKClient com comandos de escrita.
- Primeiro teste real recomendado: Fastboot em modo somente consulta, por cabo de dados OTG seguro. Se o bootloader tiver capacidade de carregar imagens na RAM, testar somente uma imagem empacotada com kernel/DTB verificados, após backup.
- A ausência de resposta do kernel referencial não comprova que o projetor rejeita Linux: ele não é da mesma placa.
