# SmartTube Chanuo A1 - perfil experimental de imagem SDR

## Dados identificados no diagnostico

- Dispositivo: Chanuo A1 / hardware mt6735 / ABI armeabi-v7a.
- RAM: 2.9 GB (reportada low-RAM); GPU Mali-T720 / GLES 3.1.
- Display Android: 1280x720 a 48.86Hz (nao garante resolucao optica nativa).
- SDK 27 (Android 8.1 API, embora a marca declare Android 12).
- AVC MediaTek: declarado 1080p60; VP9 MediaTek: ate 720p; HEVC MediaTek tem modos 720/1080p reprovados no diagnostico; AV1 ausente.
- Por seguranca, iniciar com H.264 720p30 e subir a 1080p so apos teste real de fluides.

## Funcionalidades implementadas nesta branch

Menu de configuracoes do player > **Chanuo A1 | Calibracao SDR**.
Ativar filtro de cores (desativado por padrao), preset de parede escura,
reset e escolhas de brilho/contraste/saturacao/temperatura.
Os controles so aparecem quando marca Chanuo, modelo A1 e hardware mt6735 coincidirem.
O filtro altera apenas o video, nao legendas ou menus. Parametros sao salvos.
Ao ativar pela primeira vez, **reabra o video**; a correção pode ser
desabilitada no mesmo menu se causar falhas de renderizacao ou perda de FPS.

## Limitacoes

- A correcao usa `TextureView.setLayerPaint(ColorMatrixColorFilter)`, sem copia de
  quadros para CPU, mas pode ter custo na GPU Mali-T720.
- Aumento de brilho de pixels nao aumenta lumens do projetor. 
- Efeito linear, **nao gamma verdadeiro**, sem LUT 3D, HDR ou perfil ICC.
- Resolucoes dos codecs sao declaradas pelo firmware, nao benchmark.
- Video 60 fps nao equivale a 60 quadros exibidos num framebuffer 48.86 Hz.
- Recomendacao de codec H.264/720p no player e manual neste prototipo;
  nao foi inserida uma politica automatica de selecao de formatos.
- O app ainda precisa de teste fisico neste dispositivo.
- Este prototipo inclui apenas filtro de video; nao altera sistema ou outros apps.

## Compilar

Workflow: **Compilar SmartTube Chanuo A1 ARMv7**, branch `chanuo-a1-cor-video`.
Artifact `SmartTube-ChanuoA1-ARMv7-APK` contem o APK assinado com chave
de depuracao. Se outro SmartTube instalado usa assinatura diferente, sera
necessario fazer backup e desinstalar a versao antiga ou usar applicationId
diferente em uma build seguinte.
