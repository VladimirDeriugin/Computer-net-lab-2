import java.io.*;
import java.net.*;
import java.util.zip.GZIPInputStream;

public class HTTPClient {
	private static final int DEFAULT_PORT = 80;
	private static final String CRLF = "\r\n";

	public static void main(String[] args) throws IOException {
		
		String url = args[0];
		URL parsedUrl = new URL(url);
		String host = parsedUrl.getHost();
		int port = parsedUrl.getPort() == -1 ? DEFAULT_PORT : parsedUrl.getPort();
		String path = parsedUrl.getPath().isEmpty() ? "/" : parsedUrl.getPath();
		Socket socket = new Socket(host, port);
		OutputStream outputStream = socket.getOutputStream();
		int maxRedirects = 5;
		int redirectCount = 0;

		// Send the HTTP request serveri
		String requestBody = "";
		if (args.length > 1) {
			requestBody = args[1];
		}
		String request = BuildRequest(host, path, requestBody);
		outputStream.write(request.getBytes());
		outputStream.flush();

		InputStream inputStream = socket.getInputStream();
		
		BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
			
		String headers = GetHeaders(bufferedInputStream);
		int statusCode = GetStatusCode(headers);
		while(true) {
			System.out.println("headers:\n" + headers + "\n");
			System.out.println("Response body:\n" + DecodeResponseBody(bufferedInputStream, headers) + "\n");
			
			if ((statusCode == 301 || statusCode == 302) && redirectCount < maxRedirects) {
				
				//redirect 
				String redirectUrl = GetLocationHeader(headers, "Location");
				if (redirectUrl == null) {
					socket.close();
					throw new IOException("No Location header found in redirect response");
				}
				URL redirectParsedUrl = new URL(redirectUrl);
				host = redirectParsedUrl.getHost();
				port = redirectParsedUrl.getPort() == -1 ? DEFAULT_PORT : redirectParsedUrl.getPort();
				path = redirectParsedUrl.getPath().isEmpty() ? "/" : redirectParsedUrl.getPath();
				request = BuildRequest(host, path, requestBody);

				//atvaizda
				socket.close();
				socket = new Socket(host, port);
				outputStream = socket.getOutputStream();
				outputStream.write(request.getBytes());
				outputStream.flush();
				inputStream = socket.getInputStream();
				bufferedInputStream = new BufferedInputStream(inputStream);
				headers = GetHeaders(bufferedInputStream);               
				statusCode = GetStatusCode(headers);
				redirectCount++;

			}
			else {
				break;
			}
		}

		socket.close();
	}
	
	 private static String readLine(BufferedInputStream in) throws IOException {
	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        int c;
	        while ((c = in.read()) != -1) {
	            if (c == '\r') {
	                continue;
	            } else if (c == '\n') {
	                break;
	            }
	            baos.write(c);
	        }
	        if (c == -1 && baos.size() == 0) {
	            return null;
	        }
	        return baos.toString("UTF-8");
	    }
	
	 
	
	 public static byte[] readChunkedBody(BufferedInputStream bufferedInputStream) throws IOException {
		    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		    byte[] buffer = new byte[1024];

		    while (true) {
		        StringBuilder chunkSizeLineBuilder = new StringBuilder();
		        int b;
		        while ((b = bufferedInputStream.read()) != -1) {
		            if (b == '\r') {
		                continue;
		            } else if (b == '\n') {
		                // chunk end
		                break;
		            } else {
		                chunkSizeLineBuilder.append((char) b);
		            }
		        }

		        int chunkSize = Integer.parseInt(chunkSizeLineBuilder.toString(), 16);

		        if (chunkSize == 0) {
		            break;
		        }

		        int totalBytesRead = 0;
		        while (totalBytesRead < chunkSize) {
		            int numBytesToRead = Math.min(buffer.length, chunkSize - totalBytesRead);
		            int numBytesRead = bufferedInputStream.read(buffer, 0, numBytesToRead);
		            if (numBytesRead == -1) {
		                throw new IOException("End of stream before end of chunked body");
		            }
		            byteArrayOutputStream.write(buffer, 0, numBytesRead);
		            totalBytesRead += numBytesRead;
		        }

		        // Read and discard the CRLF after the chunk
		        if (bufferedInputStream.read() != '\r' || bufferedInputStream.read() != '\n') {
		            throw new IOException("Missing CRLF after chunk data");
		        }
		    }
		    
		    return byteArrayOutputStream.toByteArray();


		}
	public static String getCharset(String headers) {
	    String charset = "UTF-8";

	    // look for Content-Type header
	    String contentType = "";
	    for (String header : headers.split("\r\n")) {
	        if (header.startsWith("Content-Type:")) {
	            contentType = header.substring("Content-Type:".length()).trim();
	            break;
	        }
	    }

	    // extract charset from Content-Type header
	    if (!contentType.isEmpty()) {
	        int i = contentType.indexOf("; charset=");
	        if (i >= 0) {
	            charset = contentType.substring(i + "; charset=".length()).trim();
	        }
	    }

	    return charset;
	}

	
	public static boolean isTansferEncodingChunked(String headers) throws IOException {
		String[] HeaderList = headers.split("\r\n");
		for (String line : HeaderList) {
			if (line.startsWith("Transfer-Encoding: chunked")) {
	            return true;
			}
		}		
		return false;          
	}
	public static int getResponseBodyLength(String headers) throws IOException {
		int length = -1;
		String[] HeaderList = headers.split("\r\n");
		for (String line : HeaderList) {
			if (line.startsWith("Content-Length: ")) {
				length = Integer.parseInt(line.substring(16));
			}
		}		
		return length;          
	}
	

	private static byte[] readFixedBody(BufferedInputStream in, int contentLength) throws IOException {
		byte[] buffer = new byte[contentLength];
	    int bytesRead = in.read(buffer, 0, contentLength);
	    if (bytesRead < contentLength) {
	    	System.out.println(bytesRead);
	    	System.out.println(contentLength);
	    	System.out.println(buffer);
	        throw new IOException("Fixed length body read error");
	    }

	    // Do something with the fixed length body data
	    return buffer;
	}
	

	
	public static byte[] DecopressGzip (byte[] compressedData) throws IOException {
		
		int bytesRead = 0;
		byte[] buffer = new byte[1024];	  
        ByteArrayOutputStream decompressedResponseBody = new ByteArrayOutputStream();
        
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
            	decompressedResponseBody.write(buffer, 0, bytesRead);
            }
        }
        return decompressedResponseBody.toByteArray();
	
	}
	
	public static String DecodeResponseBody (BufferedInputStream in, String headers) throws IOException {

		String charset = getCharset(headers);
		int chunkSize;
		byte[] responseBody = null;
		byte[] decompressedResponseBody = null;
		
		if(isTansferEncodingChunked(headers)) {
			responseBody = readChunkedBody(in);
		} else if((chunkSize = getResponseBodyLength(headers)) != -1) {
			responseBody = readFixedBody(in, chunkSize);
		}
		String ContentEncoding = GetHeaderValue(headers, "Content-Encoding");
		if(ContentEncoding != null && ContentEncoding.equals("gzip")) {
			decompressedResponseBody = DecopressGzip(responseBody);
		}else {
			decompressedResponseBody = responseBody;
		}
		return new String(decompressedResponseBody, charset);
	}

	public static String GetHeaderValue(String headers, String headerName) {
		String[] lines = headers.split("\\r?\\n");
		for (String line : lines) {
			String[] parts = line.split(": ");
			if (parts.length > 1 && parts[0].equalsIgnoreCase(headerName)) {
				return parts[1];
			}
		}
		return null;
	}


	public static String GetHeaders(BufferedInputStream in) throws IOException {
		StringBuilder headers = new StringBuilder();
		String line;

		while ((line = readLine(in)) != null && !line.isEmpty()) {
			headers.append(line).append("\r\n");
		}

		return headers.toString();
	}
	

	private static int GetStatusCode(String headers) {
		String[] lines = headers.split(CRLF);
		String[] statusLine = lines[0].split(" ");
		return Integer.parseInt(statusLine[1]);
	}

	private static String GetLocationHeader(String response, String headerName) {
		String[] lines = response.split(CRLF);
		for (String line : lines) {
			if (line.startsWith(headerName + ":")) {
				return line.substring(headerName.length() + 1).trim();
			}
		}
		return null;
	}

	private static String BuildRequest(String host, String path, String requestBody) {
		StringBuilder requestBuilder = new StringBuilder();
		requestBuilder.append("GET ").append(path).append(" HTTP/1.1").append(CRLF);
		requestBuilder.append("Host: ").append(host).append(CRLF);
		requestBuilder.append("Connection: close").append(CRLF);
		requestBuilder.append("Content-Length: ").append(requestBody.length()).append(CRLF);
		requestBuilder.append("Accept-Encoding: gzip").append(CRLF);
		requestBuilder.append(CRLF);
		requestBuilder.append(requestBody);
		return requestBuilder.toString();
	}
}