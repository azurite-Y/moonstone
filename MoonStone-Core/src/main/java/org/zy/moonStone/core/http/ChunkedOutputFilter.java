package org.zy.moonStone.core.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.http.fileupload.ByteArrayOutputStream;
import org.zy.moonStone.core.util.net.interfaces.HttpOutputBuffer;

/**
 * @dateTime 2022年12月5日;
 * @author zy(azurite-Y);
 * @description
 */
public class ChunkedOutputFilter implements OutputFilter  {
	private static final Logger logger = LoggerFactory.getLogger(ChunkedOutputFilter.class);

	private static final byte[] LAST_CHUNK_BYTES = {(byte) '0', (byte) '\r', (byte) '\n'};
	private static final String CRLF = "\r\n";
	
    private static final byte[] CRLF_BYTES = {(byte) '\r', (byte) '\n'};
    private static final byte[] END_CHUNK_BYTES = {(byte) '0', (byte) '\r', (byte) '\n', (byte) '\r', (byte) '\n'};
    
    private static final Set<String> disallowedTrailerFieldNames = new HashSet<>();

    static {
        // 始终以小写形式添加这些字符
        disallowedTrailerFieldNames.add("age");
        disallowedTrailerFieldNames.add("cache-control");
        disallowedTrailerFieldNames.add("content-length");
        disallowedTrailerFieldNames.add("content-encoding");
        disallowedTrailerFieldNames.add("content-range");
        disallowedTrailerFieldNames.add("content-type");
        disallowedTrailerFieldNames.add("date");
        disallowedTrailerFieldNames.add("expires");
        disallowedTrailerFieldNames.add("location");
        disallowedTrailerFieldNames.add("retry-after");
        disallowedTrailerFieldNames.add("trailer");
        disallowedTrailerFieldNames.add("transfer-encoding");
        disallowedTrailerFieldNames.add("vary");
        disallowedTrailerFieldNames.add("warning");
    }

    /**
     * 调用链中的下一个 {@link HttpOutputBuffer }
     */
    protected HttpOutputBuffer httpOutputBuffer;

    /**
     * Chunk header.
     */
    protected final ByteBuffer chunkHeader = ByteBuffer.allocate(10);

    protected final ByteBuffer lastChunk = ByteBuffer.wrap(LAST_CHUNK_BYTES);
    protected final ByteBuffer crlfChunk = ByteBuffer.wrap(CRLF_BYTES);
    /**
     * End chunk.[0\r\n\r\n]
     */
    protected final ByteBuffer endChunk = ByteBuffer.wrap(END_CHUNK_BYTES);

    private Response response;


	// -------------------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------------------
    public ChunkedOutputFilter() {}
    
    
	// -------------------------------------------------------------------------------------
	// 实现方法
	// -------------------------------------------------------------------------------------
    @Override
    public long getBytesWritten() {
        return httpOutputBuffer.getBytesWritten();
    }
    
    @Override
    public void setResponse(Response response) {
        this.response = response;
    }


    @Override
    public void setHttpOutputBuffer(HttpOutputBuffer buffer) {
        this.httpOutputBuffer = buffer;
    }


    @Override
    public void flush() throws IOException {
        // 此筛选器中没有缓冲的数据。刷新下一个缓冲区。
        httpOutputBuffer.flush();
    }
    
    @Override
    public void recycle() {
        response = null;
    }


	@Override
	public void end() throws IOException {
		Supplier<Map<String,String>> trailerFieldsSupplier = response.getTrailerFields();
        Map<String,String> trailerFields = null;

        if (trailerFieldsSupplier != null) {
            trailerFields = trailerFieldsSupplier.get();
        }
        
        if (logger.isDebugEnabled()) {
        	logger.debug("Write End Chunked.");
        }
        
        if (trailerFields == null) {
            // 写入结束chunked
        	httpOutputBuffer.doWrite(endChunk);
            endChunk.position(0).limit(endChunk.capacity());
        } else {
        	// 添加 Trailer 
        	httpOutputBuffer.doWrite(lastChunk);
            lastChunk.position(0).limit(lastChunk.capacity());
            
           ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
           OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.ISO_8859_1);
            for (Map.Entry<String,String> trailerField : trailerFields.entrySet()) {
                // 忽略不允许的标头
                if ( disallowedTrailerFieldNames.contains(trailerField.getKey().toLowerCase(Locale.ENGLISH)) ) {
                    continue;
                }
                if (logger.isDebugEnabled()) {
                	logger.debug("trailerField[key: " + trailerField.getKey() + "value: " + trailerField.getValue() + "].");
                }
                osw.write(trailerField.getKey());
                osw.write(':');
                osw.write(' ');
                osw.write(trailerField.getValue());
                osw.write("\r\n");
            }
            osw.close();
            httpOutputBuffer.doWrite(ByteBuffer.wrap(baos.toByteArray()));

            httpOutputBuffer.doWrite(crlfChunk);
            crlfChunk.position(0).limit(crlfChunk.capacity());
        }
        httpOutputBuffer.end();		
	}


	@Override
	public int doWrite(ByteBuffer chunk) throws IOException {
		int result = chunk.remaining();

        if (result <= 0) {
            return 0;
        }
		String hexString = Integer.toHexString(result) ;
		if (logger.isDebugEnabled()) {
			logger.debug("Write Chunked. chunkedSize: {}, hexChunkedSize: {}", result, hexString);
		}
		
		byte[] headerBytes = (hexString + CRLF).getBytes();
		this.chunkHeader.put(headerBytes , 0, headerBytes.length);
		this.chunkHeader.flip();
		httpOutputBuffer.doWrite(chunkHeader);
		
		httpOutputBuffer.doWrite(chunk);
		chunkHeader.clear();

		// 写入 "\r\n"
        httpOutputBuffer.doWrite(crlfChunk);
        crlfChunk.clear();
        return result;
	}
}
