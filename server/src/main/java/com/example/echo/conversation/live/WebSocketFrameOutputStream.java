package com.example.echo.conversation.live;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link com.example.echo.conversation.stream.ConversationStreamWriter}의 출력을 WebSocket 바이너리 메시지로 보낸다.
 *
 * 작성기는 프레임 하나를 다 쓸 때마다 flush 하므로, flush 사이에 모인 바이트 = 프레임 하나 = 바이너리 메시지 하나가 된다.
 * 전송 실패는 IOException으로 던져 작성기가 "클라이언트 끊김"으로 처리하게 한다.
 */
class WebSocketFrameOutputStream extends OutputStream {

    private final WebSocketSession session;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    WebSocketFrameOutputStream(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void write(int b) {
        pending.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        pending.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        if (pending.size() == 0) {
            return;
        }
        byte[] frame = pending.toByteArray();
        pending.reset();
        if (!session.isOpen()) {
            throw new IOException("WebSocket이 이미 닫힘");
        }
        try {
            session.sendMessage(new BinaryMessage(frame));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            // 전송 버퍼/시간 한도 초과(SessionLimitExceededException) 등 - 클라이언트가 못 받는 상태로 본다
            throw new IOException("WebSocket 전송 실패", e);
        }
    }
}
