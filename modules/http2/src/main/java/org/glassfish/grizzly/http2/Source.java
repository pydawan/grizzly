/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2016 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.http2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.memory.Buffers;

/**
 * The class represents generic source of data to be sent on {@link Http2Stream}.
 * 
 * @author Alexey Stashok
 */
public abstract class Source {
    /**
     * Returns the number of bytes remaining to be written.
     */
    public abstract long remaining();
    
    /**
     * Returns the number of bytes to be written.
     * @param length max number of bytes to return.
     * @return {@link Buffer}, which contains up to <tt>length</tt> bytes
     *          (could return less) to be written. <tt>null</tt> result is not
     *          permitted.
     * 
     * @throws Http2StreamException if an error occurs reading from the stream.
     */
    public abstract Buffer read(final int length) throws Http2StreamException;
    
    /**
     * Returns <tt>true</tt> if there is more data to be written, or <tt>false</tt>
     *              otherwise.
     */
    public abstract boolean hasRemaining();
    
    /**
     * The method is called, when the source might be released/closed.
     */
    public abstract void release();
    
    /**
     * Returns the {@link SourceFactory} associated with the {@link Http2Stream}.
     */
    public static SourceFactory factory(final Http2Stream http2Stream) {
        return new SourceFactory(http2Stream);
    }
    
    /**
     * The helper factory class to create {@link Source}s based on
     * {@link File}, {@link Buffer}, {@link String} and byte[].
     */
    public final static class SourceFactory {

        private final Http2Stream stream;

        private SourceFactory(final Http2Stream stream) {
            this.stream = stream;
        }

        /**
         * Create {@link Source} based on byte array.
         * @param array byte[] to be written.
         * 
         * @return {@link Source}.
         */
        public Source createByteArraySource(final byte[] array) {
            return createByteArraySource(array, 0, array.length);
        }
        
        /**
         * Create {@link Source} based on byte array.
         * @param array byte[] to be written.
         * @param offs the source offset in the byte array.
         * @param len the source length.
         * 
         * @return {@link Source}.
         */
        public Source createByteArraySource(final byte[] array,
                final int offs, final int len) {
            return new ByteArraySource(array, offs, len, stream);
        }

        /**
         * Create {@link Source} based on {@link Buffer}.
         * @param buffer {@link Buffer} to be written.
         * 
         * @return {@link Source}.
         */
        public Source createBufferSource(final Buffer buffer) {
            return new BufferSource(buffer, stream);
        }

        /**
         * Create {@link Source} based on file.
         * @param filename the filename.
         * 
         * @return {@link Source}.
         */
        public Source createFileSource(final String filename)
                throws FileNotFoundException {
            return createFileSource(new File(filename));
        }

        /**
         * Create {@link Source} based on {@link File}.
         * @param file the {@link File}.
         * 
         * @return {@link Source}.
         */
        public Source createFileSource(final File file)
                throws FileNotFoundException {
            return new FileSource(file, stream);
        }

        /**
         * Create {@link Source} based on {@link String}.
         * The default "ISO-8859-1" encoding will be used.
         * 
         * @param string the {@link String}.
         * 
         * @return {@link Source}.
         */
        public Source createStringSource(final String string) {
            return createStringSource(string,
                    org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET);
        }
        
        /**
         * Create {@link Source} based on {@link String}.
         * @param string the {@link String}.
         * @param charset the string character encoding {@link Charset}.
         * 
         * @return {@link Source}.
         */
        public Source createStringSource(final String string,
                final Charset charset) {
            return new BufferSource(
                    Buffers.wrap(stream.getHttp2Connection().getMemoryManager(),
                    string, charset), stream);
        }
    }
    
    /**
     * {@link Source} implementation based on {@link File}.
     */
    private static class FileSource extends Source {

        private boolean isClosed;
        private final FileInputStream fis;
        private final FileChannel fileChannel;
        private final Http2Stream stream;
        
        private long fileLengthRemaining;
        
        protected FileSource(final File file,
                final Http2Stream stream)
                throws FileNotFoundException {
            fileLengthRemaining = file.length();
            this.fis = new FileInputStream(file);
            this.fileChannel = fis.getChannel();
            this.stream = stream;
        }

        @Override
        public long remaining() {
            return fileLengthRemaining;
        }

        @Override
        public Buffer read(int length) throws Http2StreamException {
            if (isClosed) {
                throw new Http2StreamException(stream.getId(),
                        ErrorCode.INTERNAL_ERROR, "The source was closed");
            }
            
            if (fileLengthRemaining == 0) {
                return Buffers.EMPTY_BUFFER;
            }
            
            final Buffer buffer = stream.getHttp2Connection()
                    .getMemoryManager().allocate(length);
            
            final int bytesRead;
            try {
                bytesRead = (int) Buffers.readFromFileChannel(fileChannel, buffer);
            } catch (IOException e) {
                throw new Http2StreamException(stream.getId(),
                        ErrorCode.INTERNAL_ERROR, e);
            }
            
            if (bytesRead == -1) {
                throw new Http2StreamException(stream.getId(),
                        ErrorCode.INTERNAL_ERROR, "Unexpected end of file");
            }

            fileLengthRemaining -= bytesRead;
            buffer.trim();

            return buffer;
        }

        @Override
        public boolean hasRemaining() {
            return !isClosed && fileLengthRemaining > 0;
        }

        @Override
        public void release() {
            if (!isClosed) {
                isClosed = true;
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
    
    /**
     * {@link Source} implementation based on {@link Buffer}.
     */
    private static class BufferSource extends Source {
        private boolean isClosed;
        
        private Buffer buffer;

        private final Http2Stream stream;
        
        protected BufferSource(final Buffer buffer,
                final Http2Stream stream) {
            
            this.buffer = buffer;
            this.stream = stream;
        }
        
        @Override
        public long remaining() {
            return buffer.remaining();
        }

        @Override
        public Buffer read(final int length) throws Http2StreamException {
            if (isClosed) {
                throw new Http2StreamException(stream.getId(),
                        ErrorCode.INTERNAL_ERROR, "The source was closed");
            }
            
            final int remaining = buffer.remaining();
            if (length == 0 || remaining == 0) {
                return Buffers.EMPTY_BUFFER;
            }
            
            final int bytesToSplit = Math.min(remaining, length);
            final Buffer newBuf = buffer.split(buffer.position() + bytesToSplit);
            final Buffer bufferToReturn = buffer;
            buffer = newBuf;
            
            return bufferToReturn;
        }

        @Override
        public boolean hasRemaining() {
            return !isClosed && buffer.hasRemaining();
        }

        @Override
        public void release() {
            if (!isClosed) {
                isClosed = true;
                buffer.tryDispose();
            }
        }        
    }
    
    /**
     * {@link Source} implementation based on byte array.
     */
    private static class ByteArraySource extends Source {
        private boolean isClosed;
        
        private final byte[] array;
        private int offs;
        private int remaining;
        
        private final Http2Stream stream;
        
        protected ByteArraySource(final byte[] array,
                final int offs, final int len, final Http2Stream stream) {
            
            this.array = array;
            this.offs = offs;
            remaining = len;
            
            this.stream = stream;
        }
        
        @Override
        public long remaining() {
            return remaining;
        }

        @Override
        public Buffer read(final int length) throws Http2StreamException {
            if (isClosed) {
                throw new Http2StreamException(stream.getId(),
                        ErrorCode.INTERNAL_ERROR, "The source was closed");
            }
            
            if (length == 0 || remaining == 0) {
                return Buffers.EMPTY_BUFFER;
            }
            
            final int bytesToReturn = Math.min(remaining, length);
            
            final Buffer buffer = Buffers.wrap(stream.getHttp2Connection()
                    .getMemoryManager(), array, offs, bytesToReturn);
            
            offs += bytesToReturn;
            remaining -= bytesToReturn;
            
            return buffer;
        }

        @Override
        public boolean hasRemaining() {
            return !isClosed && remaining > 0;
        }

        @Override
        public void release() {
            if (!isClosed) {
                isClosed = true;
            }
        }        
    }
}
