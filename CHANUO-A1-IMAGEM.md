# Chanuo A1 cor do video - correção experimental v2

**Alteracoes feitas para resolver 'mudei os ajustes e nada aconteceu':**

1. Slider/preset agora liga automaticamente a correção quando o usuário ajusta.
2. Em vez de `TextureView.setLayerPaint`, no modo corrigido o MediaCodec envia video ao `SurfaceTexture`, que é tratado por um shader OpenGL ES 2.0 em `GLSurfaceView`.
3. Saturação 0% deixa vídeo preto e branco para verificar imediatamente se o filtro entrou em ação.
4. Gamma (65%-150%) muda os tons médios/sombras, funcionando melhor do que brilho global na projeção sobre paredes escuras.
5. Mudanças nos controles atualizam uniforms do shader sem reiniciar o decoder. Sem cópias dos quadros para CPU.
6. Quando correção desligada e vídeo reaberto, retorna ao SurfaceView nativo.

**Como testar:**

- Abra o menu de configurações do player, vá a 'Chanuo A1 | Calibracao SDR'.
- Ligue 'Ativar filtro OpenGL'. Em seguida **saia do video e abra-o de novo** para trocar a surface.
- Durante video, vá a 'Saturacao' e selecione **0% (P/B teste)**. O vídeo deve ficar preto e branco sem precisar fechar o app.
- Escolha 100% para restaurar cor; teste Gamma 80%, Brilho +8 e Contraste 95%.
- Se vídeo ficar preto ou apresentar travamentos, **desative o filtro** e reabra o vídeo; salve logs da execução.

**Limitações e avisos:**

- Não muda a potência luminosa real do projetor.
- Uma etapa extra GPU/OES custa alguma performance: medir fluidez real em 720p30 primeiro.
- Versão experimental sem teste direto em um Chanuo A1; nem todos os codecs aceitam a mesma superficie.
- O hardware detectado é ARMv7, Mali-T720 GLES3.1, framebuffer 1280x720@48.86 Hz, SDK 27.
- Se outro SmartTube com assinatura diferente estiver instalado, faça backup de dados/configurações antes de desinstalar para instalar o teste.
