/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package ninja;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Log;

import java.io.IOException;

/**
 * Handles chunks of data which contains signatures generated by the AWS SDK.
 * <p>
 * We currently do not check the signature of each token, as we checked the request signature.
 * This seems to do the job for a test / mock sever for now.
 */
class SignedChunkHandler extends sirius.web.http.InputStreamHandler {

    private int remainingBytes = 0;

    @Override
    public void handle(ByteBuf content, boolean last) throws IOException {
        if (!content.isReadable()) {
            super.handle(content, last);
            return;
        }

        while (true) {
            if (remainingBytes > 0) {
                if (content.writerIndex() - content.readerIndex() > remainingBytes) {
                    super.handle(content.slice(content.readerIndex(), remainingBytes), false);
                    content.readerIndex(content.readerIndex() + remainingBytes);
                    remainingBytes = 0;
                    skipSignature(content);
                } else {
                    super.handle(content, last);
                    remainingBytes -= content.writerIndex() - content.readerIndex();
                    return;
                }
            }

            if (remainingBytes == 0) {
                String lengthString = readChunkLengthHex(content);
                if (Strings.isEmpty(lengthString)) {
                    Log.BACKGROUND.WARN("Received a chunck without a length - Assuming 0!");
                    remainingBytes = 0;
                } else {
                    remainingBytes = Integer.parseInt(lengthString, 16);
                }
                skipSignature(content);
                if (remainingBytes == 0) {
                    skipSignature(content);
                    drainAndFlush(content);
                    return;
                }
            }
        }
    }

    private void drainAndFlush(ByteBuf content) throws IOException {
        if (content.isReadable()) {
            Log.BACKGROUND.WARN("Remaining bytes was 0 but content chunck is readable!");
            super.handle(content, false);
        }
        super.handle(Unpooled.EMPTY_BUFFER, true);
    }

    private void skipSignature(ByteBuf content) {
        while (content.isReadable()) {
            if (content.readByte() == '\r' && content.readByte() == '\n') {
                return;
            }
        }
    }

    private String readChunkLengthHex(ByteBuf content) {
        StringBuilder lengthString = new StringBuilder();
        while (content.isReadable()) {
            byte data = content.readByte();
            if (data == ';') {
                return lengthString.toString();
            }
            lengthString.append((char) data);
        }
        return lengthString.toString();
    }
}
